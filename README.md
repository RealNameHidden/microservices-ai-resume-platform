# microservices-ai-resume-platform

A polyglot microservices platform built with Spring Boot, featuring an AI-powered resume ingestion pipeline and a natural language candidate search engine — all wired together with Kafka, Elasticsearch, Redis, and the Claude API.

---

## Architecture

```
                        ┌─────────────────────────────────────────────────────┐
                        │                   Docker Compose                    │
                        │                                                     │
  Recruiter/Client      │                                                     │
       │                │                                                     │
       │  Resume Upload │  ┌──────────────────┐   Kafka: resume.uploaded     │
       └────────────────┼─▶│ Candidate Service │──────────────────────────┐  │
                        │  │    port 8083      │                           │  │
                        │  └──────────────────┘                           ▼  │
                        │          │ MySQL                    ┌──────────────┐│
                        │          ▼                          │ Resume Parser││
                        │  ┌──────────────┐                  │  port 8084   ││
                        │  │  candidatedb │                  └──────┬───────┘│
                        │  └──────────────┘                         │        │
                        │                             Claude API    │        │
                        │                             (parse PDF)   │        │
                        │                                           ▼        │
                        │  ┌────────────────────────────────────────────┐   │
                        │  │           Elasticsearch  (port 9200)        │   │
                        │  └────────────────────────────────────────────┘   │
                        │                          ▲                         │
                        │  ┌──────────────────┐    │ query                  │
       NL Search ───────┼─▶│  Search Service  │────┘                        │
                        │  │    port 8082     │──────────── Redis cache ─────│
                        │  └──────────────────┘                              │
                        │          │ Claude API (query → ES filters)         │
                        └─────────────────────────────────────────────────────┘
```

---

## Services

| Service | Port | What it does |
|---|---|---|
| **Candidate Service** | 8083 | Accepts resume uploads (PDF + metadata), fires a Kafka event |
| **Resume Parser** | 8084 | Consumes Kafka events, extracts PDF text, calls Claude to parse structured data, indexes in Elasticsearch |
| **Search Service** | 8082 | Accepts natural language queries, uses Claude to derive Elasticsearch filters, checks Redis cache, returns matching candidates |

## Infrastructure

| Component | Port | Role |
|---|---|---|
| MySQL | 3306 | Persistent store for employees, addresses, and candidate metadata |
| Kafka | 9092 | Async event bus (`resume.uploaded` topic) |
| Elasticsearch | 9200 | Full-text + filtered search index for parsed candidate profiles |
| Kibana | 5601 | Elasticsearch UI for inspection and debugging |
| Redis | 6379 | Search result cache (10-minute TTL) |
| Consul | 8500 | Service discovery (Kubernetes only) |

---

## How It Works

### Pipeline 1 — Resume Upload, Parse, and Index

This is the ingestion side of the platform. A recruiter submits a resume PDF along with a candidate's name and email. Here is what happens step by step:

**1. Candidate Service receives the upload**

The recruiter hits `POST /candidate-service/candidates/upload` with a multipart form containing the name, email, and PDF file. The Candidate Service saves the PDF to a shared volume (so the Resume Parser can read it later), writes a record to MySQL with the candidate's metadata and the file path, and then publishes a `ResumeUploadedEvent` message to the `resume.uploaded` Kafka topic. The HTTP response returns the new candidate ID immediately — the rest of the pipeline happens asynchronously.

**2. Kafka delivers the event to the Resume Parser**

Kafka acts as the buffer between the two services. The Candidate Service does not wait for parsing to finish — it fires the event and moves on. This means upload is always fast regardless of how long Claude takes to process the PDF.

**3. Resume Parser extracts and structures the resume**

The Resume Parser is a Kafka consumer listening on `resume.uploaded`. When it receives an event it reads the PDF from the shared volume, extracts the raw text, and sends that text to the Claude API (claude-haiku) with a prompt asking it to return a structured JSON object:

```json
{
  "name": "Jane Smith",
  "location": "Toronto, ON",
  "skills": ["Java", "Spring Boot", "Kubernetes", "PostgreSQL"],
  "yearsOfExperience": 5,
  "summary": "Backend engineer with 5 years of experience..."
}
```

Claude handles all the messy variation in resume formats — different layouts, different section names, inconsistent date formats — and returns a clean, consistent structure every time.

**4. Parsed candidate is indexed into Elasticsearch**

The structured data is written to the `candidates` index in Elasticsearch. From this point on the candidate is searchable. The entire parse-and-index step typically takes a few seconds end to end.

---

### Pipeline 2 — Natural Language Candidate Search

This is the query side. A recruiter types a plain English description of who they are looking for and gets back matching candidates.

**1. Search Service receives the query**

The recruiter hits `POST /search-service/search` with a JSON body like:

```json
{ "query": "Java developer in Toronto with Kubernetes experience" }
```

**2. Redis cache check**

Before doing any work, the Search Service hashes the query and checks Redis. If the same query has been run in the last 10 minutes the cached result is returned immediately with `servedFromCache: true`. This keeps repeated searches instant and reduces load on both Claude and Elasticsearch.

**3. Claude translates the query into Elasticsearch filters**

On a cache miss, the raw query string is sent to the Claude API. Claude is prompted to extract structured search intent from it and return a filter object:

```json
{
  "skills": ["Java", "Kubernetes"],
  "location": "Toronto",
  "minExperience": 0
}
```

This means recruiters never have to learn a query language — they just describe what they want in plain English and Claude handles the translation.

**4. Elasticsearch executes the query**

The Search Service builds a bool query from Claude's filters — matching on skills (term queries), location (match query), and years of experience (range query) — and runs it against the `candidates` index. The results are a ranked list of matching candidate profiles.

**5. Result is cached and returned**

The result is stored in Redis with a 10-minute TTL, then returned to the recruiter. The response includes the original query, the filters Claude derived, whether the result came from cache, and the list of matching candidates.

---

### Why Kafka instead of a direct service call?

The Candidate Service could have called the Resume Parser directly over HTTP instead of going through Kafka. The reason it does not is resilience and speed. PDF parsing with an LLM call can take several seconds. If the Resume Parser is slow or temporarily down, a direct HTTP call would either block the upload response or fail it entirely. With Kafka, the upload always succeeds immediately and the parsing happens in the background. The Resume Parser can also be scaled independently, restarted without losing events, and can replay messages if something goes wrong.

### Why Claude instead of a regex or rule-based parser?

Resumes have no standard format. One candidate writes "5 years exp", another writes "2018–2023", another lists skills in a table, another buries them in paragraph form. A rule-based parser would need hundreds of special cases and would still miss things. Claude reads the raw text the same way a human would and extracts the same fields reliably regardless of format.

---

## API Endpoints

### Candidate Service (port 8083)
| Method | Path | Description |
|---|---|---|
| `POST` | `/candidate-service/candidates/upload` | Upload a resume (multipart: `name`, `email`, `file`) |
| `GET` | `/candidate-service/candidates/{id}` | Get candidate by ID |

### Search Service (port 8082)
| Method | Path | Description |
|---|---|---|
| `POST` | `/search-service/search` | Natural language candidate search |

### Health Checks
```
GET http://localhost:8083/candidate-service/actuator/health
GET http://localhost:8084/resume-parser/actuator/health
GET http://localhost:8082/search-service/actuator/health
```

---

## Building and Running

### Prerequisites

- Docker + Docker Compose
- An [Anthropic API key](https://console.anthropic.com)

### 1. Set your API key

Create a `.env` file in the project root:

```
CLAUDE_API_KEY=sk-ant-api03-...
```

### 2. Build and start everything

```bash
docker-compose up --build
```

This starts all services and infrastructure. First build will take a few minutes.

### 3. Test the full pipeline

**Upload a resume:**
```bash
curl -X POST http://localhost:8083/candidate-service/candidates/upload \
  -F "name=John Doe" \
  -F "email=john@example.com" \
  -F "file=@/path/to/resume.pdf"
```

**Check if it was parsed and indexed** (give it a few seconds for Kafka + Claude):
```bash
curl "http://localhost:9200/candidates/_search?pretty"
```

**Watch resume-parser logs:**
```bash
docker logs geeksforgeeks-microservices-resume-parser-1 --tail=50 --follow
```

**Search candidates with natural language:**
```bash
curl -X POST http://localhost:8082/search-service/search \
  -H "Content-Type: application/json" \
  -d '{"query": "Java developer with Kubernetes experience"}'
```

---

## Project Structure

```
.
├── api-gateway/           # Spring Cloud Gateway
├── candidate-service/     # Resume upload + Kafka producer
├── resume-parser/         # Kafka consumer + Claude PDF parser + ES indexer
├── search-service/        # Natural language search via Claude + Elasticsearch
├── kube/                  # Kubernetes manifests
├── docker-compose.yml     # Full local stack
└── .env                   # API keys (not committed)
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| API Gateway | Spring Cloud Gateway |
| Inter-service (sync) | Spring Cloud OpenFeign |
| Inter-service (async) | Apache Kafka 3.8 |
| AI / LLM | Anthropic Claude (Haiku) |
| Search | Elasticsearch 8.13 |
| Cache | Redis 7 |
| ORM | Spring Data JPA |
| Database | MySQL 8 |
| Service Discovery | HashiCorp Consul (Kubernetes) |
| Observability | Spring Boot Actuator |
| Build | Maven 3 |
| Containers | Docker (multi-stage builds) |
| Orchestration | Kubernetes |

---

## Deploying to Kubernetes

```bash
kubectl apply -f kube/deployment-manifest-all.yml
```

Services connect to Consul at `consul-server.default.svc.cluster.local:8500`. Add your Claude API key as a Kubernetes secret and reference it in the deployment manifests.

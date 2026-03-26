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

## Data Flow

### Resume Upload → Parse → Index

```
POST /candidate-service/candidates/upload
  { name, email, file: resume.pdf }
          │
          ▼
  Save PDF to shared volume
  Insert into MySQL (candidatedb)
  Publish → Kafka: resume.uploaded
          │
          ▼  (async)
  Resume Parser consumes event
  Extracts text from PDF
  Calls Claude API → ParsedCandidate
    { name, location, skills[], yearsOfExperience, summary }
          │
          ▼
  Index into Elasticsearch (candidates index)
```

### Natural Language Search

```
POST /search-service/search
  { "query": "Java dev in Toronto with Kubernetes experience" }
          │
          ▼
  Call Claude API → EsFilters
    { skills: ["Java", "Kubernetes"], location: "Toronto", minExperience: 0 }
          │
          ├── Redis cache hit?  → return immediately (servedFromCache: true)
          │
          └── Cache miss → Elasticsearch bool query
                              → cache result in Redis
                              → return candidates
```

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

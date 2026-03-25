# AI Powered Resume Search Platform — Project Brief

## What We're Building
A **system design prototype** (not a production app) of an AI powered resume search platform.
The goal is to demonstrate distributed systems knowledge in interviews, not to ship a product.

**Elevator pitch:**
> "I built a distributed resume search platform using Kafka for async resume parsing,
> Elasticsearch for full text candidate search, Redis for caching, all orchestrated on
> Kubernetes with Consul for service discovery. The AI layer uses Claude API to extract
> skills from resumes and translate natural language recruiter queries into ES queries."

---

## The Two Users
- **Candidates** — upload their own resume PDF
- **Recruiters** — paste a job description, get ranked matching candidates back

---

## Architecture

```
candidate uploads resume PDF
    → candidate-service (REST) → saves PDF locally
    → fires Kafka event "resume.uploaded"
    → resume-parser consumes event
        → reads PDF
        → calls Claude API (cheap/fast model) to extract skills
        → saves structured data to MySQL
        → indexes candidate in Elasticsearch

recruiter types "find me a Java dev in Toronto with Kubernetes experience"
    → search-service
        → calls Claude API to understand JD / translate to ES query
        → checks Redis cache first
        → queries Elasticsearch
        → returns ranked candidates
```

```
                        ┌─────────────────────────────────────┐
                        │           Kubernetes (Minikube)      │
                        │                                      │
  Candidate ──REST──► candidate-service ──Kafka──► resume-parser
                              │                        │
                              │                     Claude API
                              │                        │
                           MySQL ◄────────────────────┘
                              │                        │
                              └──────────────────► Elasticsearch
                                                       ▲
  Recruiter ──REST──► search-service ──Claude API──────┘
                              │
                           Redis (cache)
                              │
                        Consul (service discovery)
```

---

## Services

### 1. candidate-service (Java / Spring Boot)
- `POST /candidates/upload` — accepts PDF, saves locally, fires Kafka event
- `GET /candidates/{id}` — fetch candidate profile
- Fires Kafka event: `resume.uploaded` with candidateId + filePath

### 2. resume-parser (Java / Spring Boot)
- Consumes Kafka topic: `resume.uploaded`
- Reads PDF from local path
- Calls **Claude API** (claude-haiku for cost efficiency) to extract:
  - skills (Java, Go, Kubernetes etc)
  - years of experience
  - location
  - education
- Saves structured data to MySQL
- Indexes candidate in Elasticsearch

### 3. search-service (Java / Spring Boot)
- `POST /search` — accepts natural language query from recruiter
- Calls **Claude API** to translate query into ES filters
- Checks **Redis** cache for identical recent queries
- Queries **Elasticsearch** for matching candidates
- Returns ranked list of candidates

### 4. job-service (Java / Spring Boot) — optional, add later
- `POST /jobs` — recruiter posts a job description
- Triggers matching against existing candidates

---

## Tech Stack & Why

| Technology | Why |
|------------|-----|
| **Kafka** | Async resume parsing — candidate shouldn't wait 10s for Claude to parse their PDF. Fire and forget. |
| **Elasticsearch** | Full text search across skills, location, experience. Inverted index makes this fast at scale. |
| **Redis** | Cache frequent recruiter searches. "find Java devs in Toronto" gets asked 100x a day — don't hit ES every time. |
| **Consul** | Service discovery — services find each other by name not hardcoded IPs. Already in existing project. |
| **Kubernetes** | Minikube locally. Each service is its own pod. Demonstrates prod-like orchestration. |
| **Claude API** | Two places: (1) parse resume PDF → structured skills, (2) translate natural language query → ES query |
| **MySQL** | Source of truth for candidate data. ES is derived from this, never the primary store. |

---

## Key Architecture Decisions (explain these in interviews!)

### Why Kafka instead of direct HTTP for parsing?
Tight coupling = fragile. If resume-parser is down, candidate upload fails. With Kafka, candidate-service
just drops the event and resume-parser catches up when it's back. Also parsing is slow (Claude API call)
— we don't want the candidate staring at a loading screen.

### Why not use ES as the primary database?
ES is eventually consistent. Data can be stale. Always pair with MySQL as source of truth.
ES is a derived copy synced via the parsing pipeline.

### Why Redis in front of ES?
Recruiter searches are repetitive. Same query hits ES thousands of times a day unnecessarily.
Redis caches results with a TTL — massive read throughput improvement.

### Why Claude Haiku for parsing?
Cost efficiency. Parsing resumes is high volume and doesn't need the smartest model.
Save the expensive model for the search translation where query understanding matters more.

---

## Existing Project to Build On
This project extends: `geeksForgeeks-microservices`

### Reuse from existing project:
- `kube/` — MySQL StatefulSet manifest ✅
- `kube/` — Consul deployment + service manifest ✅
- `docker-compose.yml` — pattern for local dev ✅
- `employee-service` — Spring Boot service structure to copy ✅
- liveness probe pattern on all services ✅

### New Kubernetes manifests needed:
- `kube/kafka-deployment.yml`
- `kube/elasticsearch-deployment.yml`
- `kube/redis-deployment.yml`
- `kube/candidate-service-deployment.yml`
- `kube/resume-parser-deployment.yml`
- `kube/search-service-deployment.yml`

---

## Elasticsearch Index Design

```json
{
  "mappings": {
    "properties": {
      "candidateId":  { "type": "keyword" },
      "name":         { "type": "text" },
      "location":     { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
      "skills":       { "type": "keyword" },
      "experience":   { "type": "integer" },
      "summary":      { "type": "text" },
      "uploadedAt":   { "type": "date" }
    }
  }
}
```

---

## Kafka Topics

| Topic | Producer | Consumer | Payload |
|-------|----------|----------|---------|
| `resume.uploaded` | candidate-service | resume-parser | `{ candidateId, filePath, uploadedAt }` |
| `resume.parsed` | resume-parser | search-service (index) | `{ candidateId, skills, location, experience }` |

---

## Claude API Usage

### 1. Resume Parsing Prompt (in resume-parser)
```
Extract the following from this resume text and return ONLY valid JSON:
{
  "name": "",
  "location": "",
  "skills": [],
  "yearsOfExperience": 0,
  "summary": ""
}

Resume text:
{resume_text}
```

### 2. Search Query Translation (in search-service)
```
Convert this recruiter query into Elasticsearch filters and return ONLY valid JSON:
{
  "skills": [],
  "location": "",
  "minExperience": 0
}

Recruiter query: {query}
```

---

## What To Build First (in order)
1. Kafka + ES + Redis kube manifests
2. candidate-service — upload endpoint + Kafka producer
3. resume-parser — Kafka consumer + Claude API integration + ES indexing
4. search-service — natural language search + Redis cache
5. Wire everything together in Minikube
6. Test end to end: upload PDF → parse → search

---

## Interview Talking Points (memorize these!)

> "I built a distributed resume search platform. Candidates upload PDFs which trigger
> Kafka events consumed by a resume parser that uses Claude API to extract structured
> skills data and index it in Elasticsearch. Recruiters can search using natural language
> — another Claude API call translates their query into ES filters. Redis caches frequent
> searches. Everything runs on Kubernetes with Consul for service discovery."

**When asked about Kafka:**
> "I used Kafka to decouple resume uploads from parsing. Parsing involves an AI API call
> which can take a few seconds — I didn't want that blocking the upload response.
> Offset management means if the parser crashes mid-job, no resume gets lost."

**When asked about ES:**
> "Elasticsearch powers the candidate search. I designed the index mapping to store
> skills as keywords for exact matching and summary as text for full text search.
> The inverted index makes skill lookups O(1) regardless of how many candidates we have."

**When asked about Redis:**
> "Recruiter searches are repetitive. I cache ES query results in Redis with a TTL
> so identical searches don't hammer Elasticsearch. Simple read-through cache pattern."

**When asked why not use ES as primary DB:**
> "ES is eventually consistent — you can have stale reads. MySQL is the source of truth.
> ES is a derived copy built from the parsing pipeline. If ES goes down we can rebuild it."

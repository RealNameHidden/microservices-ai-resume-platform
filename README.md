# GeeksForGeeks Microservices Demo

A Spring Boot microservices demo project featuring an Employee and Address service with an API Gateway, MySQL persistence, Docker containerization, and Kubernetes orchestration with Consul service discovery.

## Architecture

```
Client
  └── API Gateway (port 8083)
        ├── /employee-service/** → Employee Service (port 8080)
        └── /address-service/**  → Address Service (port 8081)
                                        └── Feign Client → Address Service
```

Both services connect to a shared MySQL database and register with Consul for service discovery in Kubernetes.

## Services

### Employee Service
- Manages employee records (name, email, age)
- Calls Address Service via Feign Client to enrich responses with address data
- Port: `8080`, context path: `/employee-service`

### Address Service
- Manages address records (city, state) linked to employees via `employee_id`
- Port: `8081`, context path: `/address-service`
- Exposes Spring Boot Actuator health endpoints

### API Gateway
- Single entry point for all traffic using Spring Cloud Gateway
- Strips service-name prefix before forwarding requests
- Port: `8083`

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/employee-service/employees/{id}` | Get employee with enriched address |
| GET | `/address-service/address/{employeeId}` | Get address by employee ID |
| GET | `/employee-service/actuator/health` | Employee service health |
| GET | `/address-service/actuator/health` | Address service health |

All routes are accessible through the API Gateway on port `8083`.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.3 |
| API Gateway | Spring Cloud Gateway |
| Inter-service comms | Spring Cloud OpenFeign |
| ORM | Spring Data JPA |
| Database | MySQL 8 |
| Service discovery | HashiCorp Consul |
| Monitoring | Spring Boot Actuator |
| Build | Maven 3 |
| Containerization | Docker (multi-stage builds) |
| Orchestration | Kubernetes |

## Data Models

**Employee**
```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@example.com",
  "age": "30",
  "address": {
    "id": 1,
    "city": "New York",
    "state": "NY"
  }
}
```

**Address**
```json
{
  "id": 1,
  "city": "New York",
  "state": "NY"
}
```

## Running Locally with Docker Compose

```bash
docker-compose up --build
```

This starts:
- `mysql-docker-container` — MySQL 8 on port `3306`, database `gfgmicroservicesdemo`
- `employee-service` — on port `8080`
- `address-service` — on port `8081`

Employee and Address services wait for MySQL to be healthy before starting.

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_HOST` | `mysql-docker-container` | MySQL hostname |
| `MYSQL_USERNAME` | `root` | MySQL user |
| `MYSQL_PASSWORD` | `password` | MySQL password |

## Deploying to Kubernetes

All manifests are in the [kube/](kube/) directory.

```bash
kubectl apply -f kube/deployment-manifest-all.yml
```

### What gets deployed

| Resource | Type | Replicas | Details |
|----------|------|----------|---------|
| MySQL | StatefulSet | 1 | 10Gi PersistentVolume, ClusterIP service |
| Employee Service | Deployment | 2 | NodePort 30080, liveness probe on `/actuator/health` |
| Address Service | Deployment | 2 | ClusterIP, liveness probe on `/actuator/health` |
| Consul | Deployment | 1 | Service discovery, HTTP on 8500, DNS on 8600 |
| Ingress | Ingress | — | Host: `employee.local`, routes to Employee Service |

Services connect to Consul via:
- `SPRING_CLOUD_CONSUL_HOST=consul-server.default.svc.cluster.local`
- `SPRING_CLOUD_CONSUL_PORT=8500`

The Employee Service is exposed externally via NodePort on `30080`. Address Service is internal-only (ClusterIP).

### Accessing via Ingress

Add the following to `/etc/hosts`:
```
<node-ip>  employee.local
```

Then access: `http://employee.local/employees/{id}`

## Project Structure

```
.
├── employee-service/          # Spring Boot employee management service
│   ├── Dockerfile             # Multi-stage Docker build
│   └── src/
├── address-service/           # Spring Boot address management service
│   ├── Dockerfile             # Multi-stage Docker build
│   └── src/
├── api-gateway/               # Spring Cloud Gateway
│   └── src/
├── kube/
│   └── deployment-manifest-all.yml   # Full Kubernetes manifest
└── docker-compose.yml         # Local development stack
```

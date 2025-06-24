# geeksForgeeks-microservices

This repository contains three simple Spring Boot services that demonstrate a basic microservice architecture.

## Modules

The project now uses a multi-module Maven build.  The root `pom.xml` declares the following modules:

- `address-service`
- `employee-service`
- `api-gateway`

Each module remains a standalone Spring Boot application but inherits common configuration from the parent project.

### Building

From the repository root you can build all services together:

```bash
./mvnw clean package
```

Individual services can still be built by running the same command inside the specific module directory.

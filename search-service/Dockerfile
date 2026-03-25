# Stage 1: Build
FROM maven:3.8.5-eclipse-temurin-17 AS build
WORKDIR /search-service

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Stage 2: Run
FROM openjdk:17-ea-17-slim
RUN mkdir /opt/search-service

COPY --from=build /search-service/target/*.jar /opt/search-service/search-service-0.0.1-SNAPSHOT.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/opt/search-service/search-service-0.0.1-SNAPSHOT.jar"]

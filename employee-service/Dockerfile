# Stage 1: Build the application
FROM maven:3.8.5-eclipse-temurin-17 AS build
WORKDIR /employee-service

# Copy the Maven project files (pom.xml and source code)
COPY pom.xml .
COPY src ./src

# Build the application (create a jar)
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM openjdk:17-ea-17-slim
RUN mkdir /opt/employee-service

# Copy the generated JAR file from the build stage
COPY --from=build /employee-service/target/*.jar /opt/employee-service/employee-service-0.0.1-SNAPSHOT.jar

# Expose the application port (default Spring Boot port)
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/opt/employee-service/employee-service-0.0.1-SNAPSHOT.jar"]

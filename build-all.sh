#!/bin/bash
set -e

SERVICES=(employee-service address-service candidate-service resume-parser search-service)

for service in "${SERVICES[@]}"; do
    echo "Building $service..."
    (cd "$service" && mvn clean package -DskipTests)
    echo "$service OK"
done

echo "All services built successfully."

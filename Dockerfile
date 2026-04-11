# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom and source
COPY pom.xml .
COPY src ./src

# Build uber-jar, skipping tests
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy uber-jar from builder
COPY --from=builder /build/target/quarkus /app/quarkus

# Set labels
LABEL service=3dime-api
LABEL version=1.0.0

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "cd /app && java -Dquarkus.http.host=0.0.0.0 -Dquarkus.http.port=8080 -jar quarkus/3dime-api-runner.jar"]

# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -q 2>/dev/null || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/transaction-service.jar app.jar
COPY opentelemetry-javaagent.jar otel-agent.jar
EXPOSE 8083
ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-Dotel.service.name=transaction-service", "-Dotel.exporter.otlp.endpoint=http://otel-collector:4317", "-Dotel.exporter.otlp.protocol=grpc", "-Dotel.metrics.exporter=otlp", "-Dotel.logs.exporter=otlp", "-jar", "app.jar"]

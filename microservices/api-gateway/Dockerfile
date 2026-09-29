# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -q \
    -Dhttps.protocols=TLSv1.2 \
    -Djdk.tls.client.protocols=TLSv1.2

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/api-gateway.jar app.jar
COPY opentelemetry-javaagent.jar otel-agent.jar
EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-Dotel.service.name=api-gateway", "-Dotel.exporter.otlp.endpoint=http://otel-collector:4317", "-Dotel.exporter.otlp.protocol=grpc", "-Dotel.metrics.exporter=otlp", "-Dotel.logs.exporter=otlp", "-jar", "app.jar"]

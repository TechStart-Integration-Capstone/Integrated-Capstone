# Pre-built: run 'mvn clean package -DskipTests' locally before docker compose build
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/analytics-service.jar app.jar
COPY opentelemetry-javaagent.jar otel-agent.jar
EXPOSE 8088
ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-Dotel.service.name=analytics-service", "-Dotel.exporter.otlp.endpoint=http://otel-collector:4317", "-Dotel.exporter.otlp.protocol=grpc", "-Dotel.metrics.exporter=otlp", "-Dotel.logs.exporter=otlp", "-jar", "app.jar"]

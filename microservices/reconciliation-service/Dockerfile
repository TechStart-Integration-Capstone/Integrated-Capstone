FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/reconciliation-service.jar app.jar
COPY opentelemetry-javaagent.jar otel-agent.jar
EXPOSE 8086
ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-Dotel.service.name=reconciliation-service", "-Dotel.exporter.otlp.endpoint=http://otel-collector:4317", "-Dotel.exporter.otlp.protocol=grpc", "-Dotel.metrics.exporter=otlp", "-Dotel.logs.exporter=otlp", "-jar", "app.jar"]

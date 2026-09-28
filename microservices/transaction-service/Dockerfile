FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/transaction-service.jar app.jar
COPY opentelemetry-javaagent.jar otel-agent.jar
EXPOSE 8083
ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-Dotel.service.name=transaction-service", "-Dotel.exporter.otlp.endpoint=http://otel-collector:4317", "-Dotel.exporter.otlp.protocol=grpc", "-Dotel.metrics.exporter=otlp", "-Dotel.logs.exporter=otlp", "-jar", "app.jar"]

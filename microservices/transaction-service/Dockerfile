# Pre-built: run 'mvn clean package -DskipTests' locally before docker compose build
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/transaction-service.jar app.jar
COPY opentelemetry-javaagent.jar otel-agent.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Xms1024m", "-Xmx1536m", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=20", "-javaagent:/app/otel-agent.jar", "-Dotel.service.name=transaction-service", "-Dotel.exporter.otlp.endpoint=http://otel-collector:4317", "-Dotel.exporter.otlp.protocol=grpc", "-Dotel.metrics.exporter=otlp", "-Dotel.logs.exporter=otlp", "-Dotel.traces.sampler=parentbased_traceidratio", "-Dotel.traces.sampler.arg=0.05", "-jar", "app.jar"]

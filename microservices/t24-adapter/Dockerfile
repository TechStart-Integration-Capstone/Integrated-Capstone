FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/t24-adapter-1.0.0-SNAPSHOT.jar app.jar
COPY opentelemetry-javaagent.jar otel-agent.jar

EXPOSE 8090

ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-jar", "/app/app.jar"]

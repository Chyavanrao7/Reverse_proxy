FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY src/ src/

RUN mkdir -p out && \
    find src -name "*.java" | xargs javac -d out

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/out ./out

EXPOSE 8080

ENTRYPOINT ["java", "-cp", "out", "proxy.ReverseProxy"]

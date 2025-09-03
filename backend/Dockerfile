FROM openjdk:17-jdk-slim

WORKDIR /app

COPY HttpBackendServer.java .

RUN javac HttpBackendServer.java

CMD ["sh", "-c", "java HttpBackendServer $PORT"]

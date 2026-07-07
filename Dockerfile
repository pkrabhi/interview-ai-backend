# Multi-stage build — keeps the final image small by not shipping the Maven cache/JDK
# build tools, only the compiled jar + a JRE to run it.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies in their own layer so `docker build` doesn't re-download them on every
# source change — only reruns if pom.xml itself changes.
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render sets $PORT at runtime; application.properties already reads it via
# server.port=${PORT:8080}, so no extra config needed here.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

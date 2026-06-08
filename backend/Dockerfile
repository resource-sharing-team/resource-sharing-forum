# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod
ENV UPLOAD_ROOT_DIR=/app/uploads

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/uploads \
    && chown -R app:app /app
COPY --from=build --chown=app:app /workspace/target/forum-backend-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://127.0.0.1:${SERVER_PORT:-8080}/api/health || exit 1

USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

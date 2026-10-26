# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src/main ./src/main

# The wrapper is not executable in the repository; invoke it through sh.
RUN --mount=type=cache,target=/root/.gradle \
    sh gradlew --no-daemon bootJar \
    && cp build/libs/*.jar /app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /app.jar ./app.jar

USER app:app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

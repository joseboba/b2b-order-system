# ── Stage 1: Build ───────────────────────────────────────────────────────────
# Full JDK + Gradle wrapper to compile and package the Spring Boot fat JAR.
# Dependencies are resolved in a separate layer so Docker cache is reused
# on subsequent builds when only source files change.
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Resolve dependencies first (cached unless build.gradle / gradle* change)
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
RUN ./gradlew dependencies --no-daemon -q

# Build the executable fat JAR; tests run in CI, not during image build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Minimal JRE image — ~200 MB lighter than the JDK image.
# Only the compiled JAR is copied from the build stage; no source or toolchain.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as a non-root system user to reduce the container's attack surface
RUN addgroup -S appgroup && adduser -S -G appgroup appuser
USER appuser

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Uses the official Maven image which includes both Maven 3.9 and Java 21.
# eclipse-temurin:21-jdk-alpine has Java but NOT Maven — that caused the build failure.
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy the POM first so Docker layer-caches the dependency download step.
# Dependencies are only re-downloaded when pom.xml changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Slim JRE-only image — Maven and JDK are not needed at runtime.
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app
COPY --from=builder /app/target/orders-api-*.jar app.jar

# -XX:+UseContainerSupport  — respect cgroup CPU/memory limits
# -XX:MaxRAMPercentage=75.0 — leave 25% headroom for OS / metaspace
# -Djava.security.egd      — faster SecureRandom for UUID generation
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

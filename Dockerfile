FROM eclipse-temurin:22-jdk-alpine AS builder

WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw package -DskipTests -q

# ── Runtime ──
FROM eclipse-temurin:22-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/fundamental-engine-*.jar app.jar

# JVM tuning for 512MB container (Render free tier)
ENV JAVA_OPTS="-Xms128m -Xmx320m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -Xss256k -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

EXPOSE 8088

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

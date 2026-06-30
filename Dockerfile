FROM eclipse-temurin:22-jre-alpine

WORKDIR /app

COPY target/fundamental-engine-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]

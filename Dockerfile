# ---- build stage: needs only Docker, no local JDK or Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- runtime stage: small JRE image running as a non root user ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S novabank && adduser -S novabank -G novabank
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
USER novabank
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]

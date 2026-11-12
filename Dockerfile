# Build stage: compiles and packages with the Maven wrapper (tests run in CI or with ./mvnw verify).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Run stage: JRE only, non-root user. The user is numeric (10001), so Kubernetes can check runAsNonRoot.
FROM eclipse-temurin:17-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build /src/target/aviation-api-wrapper-*.jar app.jar
USER 10001:10001
# 8080: API. 8081: health and metrics, for the cluster only.
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

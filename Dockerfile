# Stage 1: Build
FROM eclipse-temurin:24-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 3003
ENTRYPOINT ["java", "-jar", "app.jar"]

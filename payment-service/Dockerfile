# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy Gradle wrapper and build files first (better layer caching)
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts versions.properties ./
COPY buildSrc/build.gradle.kts ./buildSrc/build.gradle.kts
COPY buildSrc/settings.gradle.kts ./buildSrc/settings.gradle.kts
COPY buildSrc/src ./buildSrc/src
COPY store-service/build.gradle.kts ./store-service/build.gradle.kts
COPY payment-service/build.gradle.kts ./payment-service/build.gradle.kts

# Warm the dependency cache as its own layer — doesn't need app source,
# so this layer is reused whenever only source changes
RUN ./gradlew :payment-service:dependencies --no-daemon

# Now copy the actual source and build the fat jar
COPY payment-service/src/main ./payment-service/src/main
RUN ./gradlew :payment-service:buildFatJar --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# --shell /bin/false: this user can run the jar but can't get an interactive shell
RUN useradd --create-home --shell /bin/false appuser
USER appuser

COPY --from=build /app/payment-service/build/libs/*-all.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# ---------- Stage 1: Build ----------
FROM gradle:7.6.0-jdk11 AS build
WORKDIR /app

# Copy only Gradle files first (for caching dependencies)
COPY build.gradle settings.gradle gradlew gradlew.bat /app/
COPY gradle /app/gradle

# Download dependencies
RUN gradle build -x test -x checkstyleMain -x checkstyleTest -x pmdMain -x pmdTest --no-daemon || return 0

# Copy rest of the project
COPY . .

# Build Spring Boot jar
RUN gradle clean bootJar -x test -x checkstyleMain -x checkstyleTest -x pmdMain -x pmdTest --no-daemon

# ---------- Stage 2: Runtime ----------
FROM openjdk:11-jre-slim
WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]

# ==========================================
# Pixora Backend - Multi-Stage Dockerfile
# Optimized for Java 21 LTS (Render / Railway)
# ==========================================

# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21-jammy AS build
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root system user
RUN groupadd -r pixora && useradd -r -g pixora pixora
USER pixora

# Copy built JAR from build stage
COPY --from=build /app/target/pixora-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose default HTTP port
EXPOSE 8080

# Configure JVM flags for memory optimization in containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

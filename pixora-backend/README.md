# Pixora Backend

Spring Boot 3.4 backend service for the Pixora AI Professional Photo Generator.

## Tech Stack
- **Framework:** Spring Boot 3.4.3 (Java 21)
- **Security:** Spring Security
- **Data:** Spring Data JPA + PostgreSQL (Supabase)
- **Storage:** Supabase Storage REST API
- **Auth:** Firebase Admin SDK
- **AI Engine:** fal.ai Face-Preserving Generation API

## Package Structure
```
com.pixora.backend
├── config/       # Security, CORS, Dotenv, Firebase & Supabase configuration
├── controller/   # REST Controllers (Health, Auth, Photos, Test)
├── dto/          # Data Transfer Objects & API payloads
├── entity/       # JPA Entities (User, Photo, PhotoRequest)
├── exception/    # Custom exceptions and GlobalExceptionHandler
├── repository/   # Spring Data JPA repositories
├── service/      # Business logic & 3rd-party integrations (fal.ai, Supabase)
└── util/         # Image processing & validation utilities
```

## Running Locally

### Prerequisites
- Java 21 JDK installed
- Maven (or use the included `./mvnw` wrapper)

### Setup & Run
1. Copy `.env.example` to `.env` (optional for Phase 0):
   ```bash
   cp .env.example .env
   ```
2. Build and run with Maven:
   ```bash
   # On Windows PowerShell / CMD:
   .\mvnw.cmd spring-boot:run

   # On Linux / macOS:
   ./mvnw spring-boot:run
   ```
3. Verify the service is running:
   ```bash
   curl http://localhost:8080/api/health
   # Response: {"status":"ok"}
   ```

### Running Tests
```bash
.\mvnw.cmd test
```

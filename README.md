# Pixora

> **Turn Any Photo Into a Studio-Quality Professional Photo**

Pixora is an AI-powered SaaS platform that transforms casual photos into studio-grade professional headshots (for Resumes, LinkedIn, Job Applications) and compliant official identity photos (for Passports, Visas, College/Company IDs).

---

## Project Structure

```
PIXORA/
├── pixora-backend/      # Spring Boot 3.4.x (Java 21) REST API
│   ├── src/             # Application source code (Controllers, Services, Repositories, Entities)
│   ├── .env.example     # Backend environment template
│   ├── pom.xml          # Maven build configuration
│   └── README.md        # Backend documentation & setup guide
│
└── pixora-frontend/     # Vanilla HTML5, CSS3, ES6 JavaScript frontend
    ├── index.html       # Landing page & health monitor
    ├── login.html       # Firebase Google Sign-In
    ├── upload.html      # Image upload flow with client-side validation
    ├── customize.html   # Purpose, style, clothing & background selector
    ├── result.html      # Before/After comparison slider & high-res download
    ├── my-photos.html   # User photo dashboard & library
    ├── css/style.css    # Modern dark mode design system
    ├── js/api.js        # Centralized API configuration
    ├── vercel.json      # Vercel static deployment config
    └── README.md        # Frontend documentation & setup guide
```

---

## Quick Start

### 1. Run the Backend
```bash
cd pixora-backend
.\mvnw.cmd spring-boot:run   # Windows
# or: ./mvnw spring-boot:run # Linux / macOS
```
Backend health endpoint: `http://localhost:8080/api/health`

### 2. Run the Frontend
```bash
cd pixora-frontend
python -m http.server 3000
# or: npx serve . -p 3000
```
Open `http://localhost:3000` in your web browser.

---

## Technology Stack

- **Backend:** Spring Boot 3.4.3 (Java 21), Spring Data JPA, Spring Security, Lombok
- **Database & Storage:** Supabase (PostgreSQL + S3 Storage)
- **Authentication:** Firebase Auth (Google Sign-In)
- **AI Processing:** fal.ai Face-Preserving Image Generation API + ImageIO
- **Frontend:** Vanilla HTML5, CSS3, JavaScript ES6 (Zero framework lock-in)
- **Deployment:** Render / Railway (Backend) & Vercel (Frontend)

# Pixora Frontend

Vanilla HTML5, CSS3, and JavaScript frontend for Pixora — AI-Powered Professional Photo Generator.

## Tech Stack
- **Languages:** HTML5, CSS3, Vanilla ES6 JavaScript (No frameworks, zero heavy dependencies)
- **Deployment:** Vercel Static Hosting (`vercel.json`)
- **API Integration:** Connects to `pixora-backend` Spring Boot API via `js/api.js`

## Directory Structure
```
pixora-frontend/
├── index.html        # Landing page with live backend health monitor
├── login.html        # Firebase Google sign-in (Phase 2)
├── upload.html       # Photo upload flow with validation (Phase 4)
├── customize.html    # Style & purpose picker (Phase 5)
├── result.html       # Before/after comparison slider & download (Phase 7)
├── my-photos.html    # Saved photo library (Phase 8)
├── css/
│   └── style.css     # Modern, clean dark theme design system
├── js/
│   ├── api.js        # Single BASE_URL config & fetch wrapper
│   └── main.js       # Dynamic landing page logic & health checker
├── assets/           # Static icons & sample assets
└── vercel.json       # Vercel configuration
```

## Running Locally

Serve the `pixora-frontend` directory with any static server:
```bash
# Using npx serve:
npx serve pixora-frontend -p 3000

# Using python:
python -m http.server 3000 --directory pixora-frontend

# Or use VS Code Live Server extension on port 5500
```
Open `http://localhost:3000` in your browser.

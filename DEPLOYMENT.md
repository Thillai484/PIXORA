# Pixora — Production Deployment Guide

This guide walks you through deploying **Pixora Backend** (Spring Boot on Render/Railway) and **Pixora Frontend** (Vanilla HTML/CSS/JS on Vercel).

---

## 1. Prerequisites & Credentials Checklist
Ensure you have the following accounts and credentials ready:
- **GitHub Repository**: `https://github.com/Thillai484/PIXORA`
- **Firebase Project**: `pixora-9223f` (Google Auth enabled in Firebase Console)
- **Supabase Project**: Postgres database + `photos` Storage bucket
- **fal.ai Account**: API Key with Flux image generation access
- **Render / Railway Account**: For backend container hosting
- **Vercel Account**: For frontend static hosting

---

## 2. Supabase Setup (Database & Storage)

1. **Create Storage Bucket**:
   - Go to **Supabase Dashboard &rarr; Storage**.
   - Create a new public bucket named `photos`.
   - Under bucket policies, allow `SELECT`, `INSERT`, `UPDATE`, and `DELETE` for authenticated / public users.
2. **Retrieve Connection Details**:
   - Go to **Project Settings &rarr; Database**.
   - Copy the **Connection String** (Transaction / Session Pooler mode on port 6543 or direct port 5432).
   - Copy the **Project URL** (`https://<ref>.supabase.co`) and **anon/service_role API Key**.

---

## 3. Deploying Backend to Render / Railway

### Option A: Render (Recommended)
1. Log in to [Render](https://render.com).
2. Click **New + &rarr; Web Service**.
3. Connect your GitHub repository (`Thillai484/PIXORA`).
4. Select **Docker** environment:
   - **Root Directory**: `pixora-backend`
   - **Dockerfile Path**: `Dockerfile`
   - **Instance Type**: Free or Starter
5. Add the following **Environment Variables**:
   ```ini
   PORT=8080
   SPRING_PROFILES_ACTIVE=prod
   DATABASE_URL=jdbc:postgresql://<supabase-host>:6543/postgres?sslmode=require
   DATABASE_USERNAME=postgres.<project-ref>
   DATABASE_PASSWORD=<supabase-db-password>
   SUPABASE_URL=https://<project-ref>.supabase.co
   SUPABASE_KEY=<supabase-anon-or-service-key>
   FAL_AI_API_KEY=<your-fal-ai-api-key>
   FIREBASE_PROJECT_ID=pixora-9223f
   FRONTEND_URL=https://<your-vercel-domain>.vercel.app
   ```
6. Set **Health Check Path** to `/api/health`.
7. Click **Create Web Service**. Render will build and launch your container.
8. Copy your live backend URL (e.g. `https://pixora-backend.onrender.com`).

---

## 4. Deploying Frontend to Vercel

1. Log in to [Vercel](https://vercel.com).
2. Click **Add New &rarr; Project**.
3. Import `Thillai484/PIXORA`.
4. In the configuration screen:
   - **Framework Preset**: Other
   - **Root Directory**: `pixora-frontend`
5. Click **Deploy**.
6. Update `pixora-frontend/js/api.js`:
   ```javascript
   export const BASE_URL = isLocalhost 
       ? 'http://localhost:8080/api' 
       : 'https://pixora-backend.onrender.com/api'; // <--- Your live Render URL
   ```
7. Commit and push to trigger Vercel auto-deployment.

---

## 5. Firebase Google Login Domain Authorization

1. Open the [Firebase Console](https://console.firebase.google.com).
2. Select project **pixora-9223f**.
3. Navigate to **Authentication &rarr; Settings &rarr; Authorized Domains**.
4. Add your Vercel domain (e.g. `pixora.vercel.app`) and your Render domain.

---

## 6. End-to-End Live Verification Checklist

- [ ] Open `https://<your-vercel-domain>.vercel.app` &rarr; Landing page animated glowing orbs load and health check indicates `Backend connected ✓`.
- [ ] Click "Sign In" &rarr; Firebase Google Login modal pops up and signs in.
- [ ] Navigate to `/upload.html` &rarr; Drag and drop portrait image, preview displays metadata.
- [ ] Choose preset on `/customize.html` &rarr; Select "Passport Photo" or "Professional Custom".
- [ ] Click "Generate Photo" &rarr; Progress tracker pulses and transitions to Before/After slider.
- [ ] Click "Download HD Photo" &rarr; Image downloads to disk as `pixora-passport-1.png`.
- [ ] Navigate to `/my-photos.html` &rarr; Generated photo is displayed in your gallery collection.
- [ ] Navigate to `/pack.html` &rarr; Generate 3-in-1 batch pack and download `pixora-photo-pack.zip`.

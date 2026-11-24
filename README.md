# BookNest: an AI-assisted library web app

Full Stack Development course project, NMIT Bengaluru (2026-27).
Team: Bhavy Shukla (1NT23CS042), Bhargavi Saraswat (1NT23CS041), Dhruv Sharma (1NT23CS063).

Readers browse 49 books, rate and save them, and get AI recommendations and chat help.

- **Frontend:** React 18 + Vite, React Router 6, plain CSS (dark mode, mobile layout)
- **Backend:** Spring Boot 3.3 (Java 17), Spring Data JPA, H2 database, Bean Validation, BCrypt
- **AI:** a TF-IDF recommender written from scratch in Java, plus Google Gemini for the BookBot chatbot and the "why this book" sentences. Without an API key, the app falls back to a rule-based engine.

## Features

- Registration and login with signed tokens, roles USER and ADMIN
- Catalog with genre shelves, keyword search, sorting and AI smart search (ranks by meaning)
- Book pages with 1-5 star ratings, save to "My list", and similar books
- "Picked for you": personalised recommendations with a reason under each book
- BookBot: a chat panel that answers from the catalog (RAG) and shows clickable book cards
- Admin: add, edit and delete books
- 19 REST endpoints under `/api`

## Run

Requirements: JDK 17+, Maven 3.8+, Node.js 18+.

```
# terminal 1: backend on http://localhost:8080
cd backend
mvn spring-boot:run

# terminal 2: frontend on http://localhost:5173
cd frontend
npm install
npm run dev
```

On Windows you can double-click `run-backend.bat` and then `run-frontend.bat`.

Demo logins: `student@booknest.com` / `student123`, `admin@booknest.com` / `admin123`.

## Turn on the Gemini chatbot (optional)

Get a free key at https://aistudio.google.com/apikey, copy `backend/application-local.properties.example`
to `backend/application-local.properties`, paste the key and restart the backend.
That file is git-ignored, so the key is never committed.

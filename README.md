# InterviewOS 

**An adaptive, AI-powered interview preparation platform that personalizes technical interviews, evaluates answers, and tracks interview performance.**

InterviewOS combines resume-based question generation, job-role-specific technical interviews, adaptive difficulty, and AI-assisted answer evaluation to create a more personalized interview practice experience.

## ✨ Features

- **Resume-Based Interviews:** Upload PDF or DOCX resumes and practice questions tailored to your background, skills, and projects.
- **Role-Based Preparation:** Practice interviews for different technical roles and areas of interest.
- **Multiple Interview Modes:** Choose from Technical, DSA, HR, Project-Based, and AI/ML interviews.
- **Adaptive Difficulty:** Questions become easier or harder based on recent performance and topic coverage.
- **AI-Powered Evaluation:** Optionally use Groq to assess open-ended answers, generate contextual follow-up questions, and summarize interview reports.
- **Automatic Fallback:** Continue practicing with rule-based follow-ups and heuristic evaluation when AI services are unavailable.
- **Interview Reports:** Review scores, performance summaries, difficulty changes, and areas for improvement.
- **Asynchronous Processing:** Use Kafka-backed jobs for interview evaluation and report processing.
- **Progress Tracking:** Track practice activity, bookmark questions, and discover suggested topics.
- **Secure Authentication:** JWT-based authentication with rate limiting and token revocation support.
- **Speech Support:** Dictate answers and listen to interviewer messages in supported browsers.

## 🛠️ Tech Stack

| Layer | Technologies |
|---|---|
| Frontend | React, Vite, TypeScript, Tailwind CSS |
| Backend | Java 21, Spring Boot 3, Maven |
| Database | PostgreSQL, Flyway |
| Caching and Rate Limiting | Redis |
| Asynchronous Jobs | Apache Kafka |
| AI Integration | Groq API |
| Testing | JUnit, Vitest, integration tests |
| Deployment and Development | Docker, Docker Compose |

## 🏗️ Architecture

```text
                  ┌──────────────────┐
                  │   React Frontend │
                  │  Vite + TypeScript│
                  └────────┬─────────┘
                           │ REST API
                  ┌────────▼─────────┐
                  │  Spring Boot API │
                  │ Authentication   │
                  │ Interview Engine │
                  │ Answer Evaluation│
                  └───────┬──────────┘
                          │
            ┌─────────────┼─────────────┐
            │             │             │
       ┌────▼────┐  ┌─────▼────┐  ┌────▼────┐
       │PostgreSQL│  │  Redis   │  │  Kafka  │
       │   Data   │  │  Cache   │  │  Jobs   │
       └─────────┘  └──────────┘  └─────────┘
                          │
                  ┌───────▼────────┐
                  │    Groq API    │
                  │ Optional AI    │
                  └────────────────┘
```

## 🚀 Getting Started

### Prerequisites

- Docker Desktop
- Git
- A Groq API key for AI-powered features (optional)

### 1. Clone the repository

```bash
git clone https://github.com/tejaswini325/InterviewOS.git
cd InterviewOS
```

### 2. Configure environment variables

Create your local environment file from the example:

**Windows Command Prompt**

```cmd
copy .env.example .env
```

**PowerShell**

```powershell
Copy-Item .env.example .env
```

Open `.env` and configure the required values, including:

- `DB_PASSWORD`
- `JWT_SECRET` — use a strong secret of at least 32 bytes
- `GROQ_API_KEY` — your private Groq API key
- `GROQ_MODEL` — if supported by your application configuration

Use the variable names and defaults defined in `.env.example` and your application configuration. Never commit your real `.env` file or API keys to GitHub.

### 3. Start the application

From the project root, run:

```bash
docker compose up --build -d
```

Check the running services:

```bash
docker compose ps
```

View backend logs:

```bash
docker compose logs -f backend
```

### 4. Open InterviewOS

| Service | URL |
|---|---|
| Frontend | http://localhost:8081 |
| Backend health | http://localhost:8080/actuator/health |
| Swagger API documentation | http://localhost:8080/swagger-ui.html |

The exact URLs depend on your Docker Compose port configuration.

## 🤖 How AI Integration Works

Groq is an optional AI provider that enhances the interview workflow.

It can be used for:

1. Generating contextual follow-up questions based on a candidate's answer.
2. Assessing open-ended answers and combining AI assessment with heuristic scoring.
3. Producing interview report summaries.

The rule-based engine provides a fallback when AI requests fail or the service is unavailable. AI-assisted scoring is bounded and combined with deterministic evaluation rather than being treated as an unrestricted score.

AI requests are made for supported interview events, not every user-interface action.

## 🧠 Adaptive Interview Engine

InterviewOS adjusts practice difficulty based on recent performance.

- Two consecutive answers scoring at least 75% can increase difficulty.
- Two consecutive answers scoring below 40% can decrease difficulty.
- Topic selection considers coverage and previous performance.
- Weak topics can be presented at a lower difficulty after sufficient attempts.

These rules are configurable and help keep practice focused on the candidate's learning needs.

## 🧪 Testing

### Backend

```bash
cd backend
mvn test
```

### Frontend

```bash
cd frontend
npm install
npm test
npm run build
```

Run the tests before deployment to verify the current source code and dependencies. Test results can vary with code changes and environment configuration.

## 🔐 Security Notes

- Keep API keys and production credentials in environment variables or a secrets manager.
- Never commit `.env` files containing real credentials.
- Use a strong JWT secret and secure database credentials.
- Configure CORS, HTTPS, and production service access before exposing the application publicly.
- InterviewOS does not execute submitted coding answers in a bundled code-execution sandbox.

## 🗺️ Roadmap

- Improve resume-aware interview personalization.
- Expand interview question coverage and evaluation rubrics.
- Enhance interview analytics and progress insights.
- Deploy the platform with production-ready configuration.
- Explore safe, isolated code execution for programming interviews.

## 👩‍💻 Author

**Tejaswini M V**

Computer Science and Artificial Intelligence Engineering

- GitHub: [@tejaswini325](https://github.com/tejaswini325)

## 📄 License

Add a `LICENSE` file if you intend to publish this project under an open-source license.

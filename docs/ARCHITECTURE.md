# Architecture notes

## Layout
`backend/` Spring Boot (controller, service, repository, domain, dto, security, evaluation, adaptive, ai, jobs, exception, config). `frontend/` React SPA. `tools/gen_seed.py` generates the question-bank migration (`V2__seed_questions.sql`, 40 questions: 19 MCQ, 6 short, 11 long, 4 coding). Edit the generator, not the SQL.

## Data model
users, candidate_profiles, questions, question_tags, interview_sessions, interview_questions, candidate_answers, answer_evaluations, skill_scores, evaluation_jobs, interview_reports (Flyway `V1__schema.sql`).
Constraints that carry behaviour: `uq_iq_session_question` (no repeats), `uq_iq_session_position`, `candidate_answers.interview_question_id UNIQUE` (duplicate-submission guard), `evaluation_jobs.idempotency_key UNIQUE`, `uq_skill_user_topic`.
Deliberate denormalization: `interview_sessions.topics` (CSV, filter only), adaptation audit columns on `interview_questions`, `interview_reports.report_json` snapshot, JSON text for options/rubric/test cases. Emails are stored lower-cased (the schema avoids a functional index so the same migrations run on H2 in tests).

## Flow
1. `POST /interviews` selects the first question. `POST /next-question` is idempotent: it returns the current unanswered question (resume / double-click safe) or selects the next one.
2. `POST /answers` locks the session row, rejects duplicates by returning the original result, scores provisionally, updates the adaptive state, and enqueues an ANSWER job if refinement is needed.
3. `POST /complete` enqueues a REPORT job. The report job refines any pending evaluations, builds the report from persisted data, optionally asks the AI for a summary, and stores a snapshot. Skill scores are an EMA (alpha 0.4) updated once per report.

## Decisions and trade-offs
- Ownership is part of every query; other candidates' ids return 404, not 403.
- The JWT is stored in localStorage (XSS-readable) for simplicity; tokens expire in 60 min and logout is enforced server-side through a Redis denylist (fails open if Redis is down).
- The rate limiter keys on `remoteAddr`; behind a proxy the `docker` profile enables forwarded-header handling. Do not expose the backend port directly in production if you rely on it.
- Adaptation uses the provisional deterministic score; LLM refinement can change the stored final score but never retroactively changes served questions.
- Unanswered questions count as 0; unevaluated answers (score null) are excluded from averages.
- Retry backoff is a flat ~5 s between redeliveries, not exponential.

## Known limitations
- Backend and Docker were not compiled or run in the authoring environment (see README).
- No admin bootstrap: promote a user with `UPDATE users SET role='ADMIN' WHERE email='...'`.
- Keyword rubrics are crude: they reward vocabulary coverage, not correctness of reasoning. Treat short/long scores as an approximation.
- History does N+1 count queries per page (page size capped at 50).
- Frontend bundle is ~640 kB (no code splitting).
- No sandbox for code execution is provided.

## v2 additions (modes, conversation, Groq)
- Migrations: `V3` adds `interview_sessions.mode`, `questions.follow_ups`, `candidate_profiles.weekly_goal`, tables `interview_turns` and `bookmarks` (all additive, existing data untouched); `V4` seeds HR/PROJECT/ML questions.
- Conversation is derived from existing rows (questions, answers) plus `interview_turns` (follow-up and reply). At most one follow-up per question (unique constraint).
- Follow-up generation runs after the answer transaction commits and is not itself transactional, so a slow AI call never holds a DB connection or the session lock. A follow-up reply re-scores the heuristic on answer + reply (scores only go up) and does not re-adapt difficulty.
- `GroqAiProvider` uses the JDK HTTP client against `{baseUrl}/chat/completions`. Beans are selected by whether `GROQ_API_KEY` is set (`NoopAiProvider` otherwise). The default model is `openai/gpt-oss-120b` with `reasoning_effort=low`; the name lives only in `application.yml` / `GROQ_MODEL`.
- Report snapshots from before v2 lack `mode`, `aiUsed`, `evaluationKind`, `followUps`; the UI treats them as optional.
- Known limits: heuristic scoring is keyword-based; AI output quality depends on the model and is not validated beyond schema checks; the first-run model name should be re-verified against Groq's current model list.

## Resume-personalized interviews
- `POST /api/resume` accepts authenticated PDF or DOCX uploads up to 5 MB. Extracted text (capped at 20,000 characters) is stored privately on the candidate profile; `GET` reports upload status and `DELETE` removes it.
- The `RESUME` mode alternates resume-focused and role/topic technical questions. Groq generates structured questions when configured; otherwise a deterministic template generator uses detected technologies and the target role.
- Each session's generated questions are stored in the existing `questions` table with a session owner and served through the standard adaptive/session-answer/report flow. They are not added to the shared question bank.
- Groq receives the extracted resume text only for question generation. Removing or replacing the uploaded resume affects future interviews, not questions already generated for a session.

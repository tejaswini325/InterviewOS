ALTER TABLE candidate_profiles ADD COLUMN resume_text TEXT;
ALTER TABLE candidate_profiles ADD COLUMN resume_filename VARCHAR(255);
ALTER TABLE candidate_profiles ADD COLUMN resume_updated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE questions ADD COLUMN personalized_session_id BIGINT REFERENCES interview_sessions (id) ON DELETE CASCADE;
ALTER TABLE questions ADD COLUMN personalized_position INT;
CREATE INDEX ix_questions_personalized_session_position
    ON questions (personalized_session_id, personalized_position);

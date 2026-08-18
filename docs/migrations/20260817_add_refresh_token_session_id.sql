ALTER TABLE refresh_token
    ADD COLUMN session_id VARCHAR(36) NULL AFTER installation_id,
    ADD CONSTRAINT uk_refresh_token_session_id UNIQUE (session_id);

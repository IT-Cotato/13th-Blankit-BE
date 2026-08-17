ALTER TABLE refresh_token
    ADD COLUMN installation_id VARCHAR(255) NULL AFTER expires_at;

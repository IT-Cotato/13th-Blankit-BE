ALTER TABLE push_notification_job
    ADD COLUMN processing_started_at DATETIME(6) NULL AFTER next_retry_at,
    ADD COLUMN retry_fids TEXT NULL AFTER processing_started_at,
    ADD INDEX idx_push_job_processing_lease (status, processing_started_at);

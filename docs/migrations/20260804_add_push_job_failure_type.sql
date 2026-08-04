ALTER TABLE push_notification_job
    ADD COLUMN failure_type VARCHAR(30) NULL AFTER retry_fids;

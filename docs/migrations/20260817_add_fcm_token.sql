ALTER TABLE push_subscription
    ADD COLUMN fcm_token VARCHAR(512) NULL AFTER firebase_installation_id,
    ADD CONSTRAINT uk_push_subscription_fcm_token UNIQUE (fcm_token);

-- 기존 구독에는 token이 없으므로 새 클라이언트 등록이 완료될 때까지 발송 대상에서 제외한다.
UPDATE push_subscription
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE fcm_token IS NULL;

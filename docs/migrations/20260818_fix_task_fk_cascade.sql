-- task 삭제 시 FK 제약 위반 방지 — ON DELETE CASCADE 추가
-- daily_recommendation_item, repeat_rule이 task 삭제 전에 자동 삭제되도록 처리

ALTER TABLE daily_recommendation_item DROP FOREIGN KEY FKsgckrpgt6tlyhgau4pl4btqyy;
ALTER TABLE daily_recommendation_item ADD CONSTRAINT fk_daily_recommendation_item_task
    FOREIGN KEY (task_id) REFERENCES task (task_id) ON DELETE CASCADE;

ALTER TABLE repeat_rule DROP FOREIGN KEY FKqqdp2hhqkw9866jgtl1cxq869;
ALTER TABLE repeat_rule ADD CONSTRAINT fk_repeat_rule_task
    FOREIGN KEY (task_id) REFERENCES task (task_id) ON DELETE CASCADE;

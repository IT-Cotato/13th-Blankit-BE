-- playlist_item.task_id FK에 ON DELETE CASCADE 추가
-- Hibernate 자동 생성 FK(CASCADE 없음)를 schema.sql 정의와 일치하도록 교체
ALTER TABLE playlist_item DROP FOREIGN KEY FKclm9axioqjlobljaeq9jdeg5g;

ALTER TABLE playlist_item ADD CONSTRAINT fk_playlist_item_task
    FOREIGN KEY (task_id) REFERENCES task (task_id) ON DELETE CASCADE;

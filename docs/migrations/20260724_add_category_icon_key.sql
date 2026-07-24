-- category.icon_key 배포 마이그레이션
-- 실행 순서: nullable 컬럼 추가 -> 기존 행 backfill -> NOT NULL 전환

ALTER TABLE category
    ADD COLUMN icon_key VARCHAR(100) NULL
        COMMENT '프론트엔드에서 관리하는 카테고리 아이콘 식별 키'
        AFTER color;

UPDATE category
SET icon_key = CASE name
                   WHEN '학업' THEN 'book'
                   WHEN '일상' THEN 'daily'
                   WHEN '기념일' THEN 'calendar'
                   ELSE 'book'
    END
WHERE icon_key IS NULL OR TRIM(icon_key) = '';

ALTER TABLE category
    MODIFY COLUMN icon_key VARCHAR(100) NOT NULL
        COMMENT '프론트엔드에서 관리하는 카테고리 아이콘 식별 키';

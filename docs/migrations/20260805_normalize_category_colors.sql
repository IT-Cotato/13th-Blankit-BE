-- 카테고리 고정 10색 팔레트 적용 마이그레이션 (MySQL 8.0+)
-- 정책:
-- 1. 사용자별 활성 카테고리를 기본 카테고리 이름, 정렬 순서, 생성 순서로 정렬한다.
-- 2. 첫 10개는 고정 팔레트 색상을 중복 없이 재배정한다.
-- 3. 10개를 초과한 활성 카테고리는 비활성화한다.

START TRANSACTION;

CREATE TEMPORARY TABLE category_color_migration (
    category_id BIGINT PRIMARY KEY,
    color_order INT NOT NULL
);

INSERT INTO category_color_migration (category_id, color_order)
SELECT ranked.category_id, ranked.color_order
FROM (
    SELECT category_id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY CASE name
                            WHEN '학업' THEN 0
                            WHEN '일상' THEN 1
                            WHEN '기념일' THEN 2
                            ELSE 3
                        END,
                        is_default DESC,
                        sort_order,
                        created_at,
                        category_id
           ) AS color_order
    FROM category
    WHERE is_deleted = FALSE
) ranked;

UPDATE category category_row
JOIN category_color_migration migration
  ON migration.category_id = category_row.category_id
SET category_row.is_deleted = TRUE
WHERE migration.color_order > 10;

UPDATE category category_row
JOIN category_color_migration migration
  ON migration.category_id = category_row.category_id
SET category_row.color = CASE migration.color_order
    WHEN 1 THEN '#FC5F5F'
    WHEN 2 THEN '#FF9A33'
    WHEN 3 THEN '#FBF965'
    WHEN 4 THEN '#D3FB65'
    WHEN 5 THEN '#5BE478'
    WHEN 6 THEN '#5BE4CB'
    WHEN 7 THEN '#6FD4FF'
    WHEN 8 THEN '#B3BBFA'
    WHEN 9 THEN '#F2B3FA'
    WHEN 10 THEN '#C5C9CD'
END
WHERE migration.color_order <= 10;

DROP TEMPORARY TABLE category_color_migration;

COMMIT;

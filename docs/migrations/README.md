# DB 마이그레이션

이 디렉터리의 SQL 파일은 기존 운영 DB에 수동으로 한 번만 적용합니다.
신규 DB는 [`docs/schema.sql`](../schema.sql)로 생성하므로 별도 마이그레이션이 필요하지 않습니다.

## Web Push 테이블 추가

Firebase FID 구독 및 예약 작업을 위해 애플리케이션 배포 전에 아래 파일을 실행합니다.

```text
20260729_add_web_push.sql
20260730_add_push_delivery_recovery.sql
20260804_add_push_job_failure_type.sql
```

## `category.icon_key` 추가

애플리케이션 배포 전에 아래 파일을 MySQL에서 실행합니다.

```text
20260724_add_category_icon_key.sql
```

파일 내부에서 다음 순서로 실행됩니다.

1. `icon_key`를 nullable 컬럼으로 추가합니다.
2. 기존 기본 카테고리는 `학업=book`, `일상=daily`, `기념일=calendar`로 채웁니다.
3. 그 외 기존 카테고리는 fallback 아이콘인 `book`으로 채웁니다.
4. 모든 행을 채운 후 `NOT NULL` 제약을 적용합니다.

마이그레이션 완료 후 아래 쿼리 결과가 `0`인지 확인합니다.

```sql
SELECT COUNT(*)
FROM category
WHERE icon_key IS NULL OR TRIM(icon_key) = '';
```

## 카테고리 고정 색상 적용

카테고리 색상 정책 배포 전에 아래 파일을 MySQL 8.0 이상에서 한 번 실행합니다.

```text
20260805_normalize_category_colors.sql
```

파일 내부에서 사용자별 활성 카테고리를 다음 정책으로 정리합니다.

1. `학업`, `일상`, `기념일`을 먼저 배치하고 정렬 순서와 생성 순서를 적용합니다.
2. 첫 10개 카테고리에 고정 팔레트 색상을 중복 없이 재배정합니다.
3. 10개를 초과한 카테고리는 고정 팔레트에 배정할 색상이 없으므로 비활성화합니다.
4. 이미 삭제된 카테고리는 변경하지 않습니다.

마이그레이션 완료 후 아래 쿼리 결과가 모두 `0`인지 확인합니다.

```sql
SELECT COUNT(*)
FROM category
WHERE is_deleted = FALSE
  AND color NOT IN (
      '#FC5F5F', '#FF9A33', '#FBF965', '#D3FB65', '#5BE478',
      '#5BE4CB', '#6FD4FF', '#B3BBFA', '#F2B3FA', '#C5C9CD'
  );

SELECT COUNT(*)
FROM (
    SELECT user_id, color
    FROM category
    WHERE is_deleted = FALSE
    GROUP BY user_id, color
    HAVING COUNT(*) > 1
) duplicated_active_colors;
```

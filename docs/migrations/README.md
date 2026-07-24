# DB 마이그레이션

이 디렉터리의 SQL 파일은 기존 운영 DB에 수동으로 한 번만 적용합니다.
신규 DB는 [`docs/schema.sql`](../schema.sql)로 생성하므로 별도 마이그레이션이 필요하지 않습니다.

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

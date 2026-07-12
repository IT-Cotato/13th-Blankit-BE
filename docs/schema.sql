-- ============================================================
-- Blankit DB Schema (v2 - 리뷰 반영판)
-- 기준: ERD v1 + 기능명세서/로직문서 대조 리뷰 반영
--
-- [v1 대비 변경 요약]
--  1. task.similar_task_id 추가        : 예상 시간 보정 로직용 (비슷한 과업 연결)
--  2. task.repeat_type 삭제            : repeat_rule과 중복 → repeat_rule을 단일 소스로
--  3. playlist_item.source_mode 추가   : 모드별 필터링 / 모드 단위 일괄 삭제용
--  4. feedback.is_draft 추가           : 피드백 임시저장 구분 (통계 계산 시 draft 제외)
--  5. category.is_deleted 추가         : soft delete (완료 과업의 카테고리 3년 보존)
--  6. repeat_rule.task_id UNIQUE       : 과업당 반복 규칙 1개 보장
--  7. user.timetable_start/end_time 추가 : 시간표 표시 범위 설정 (기본 08:00~24:00)
--  8. user.recommended_daily_time 삭제 : 매일 재계산되는 값 → daily_recommendation으로 일원화
--  9. search_history 테이블 신규        : 최근 검색어 (최대 5개 표시는 애플리케이션에서 처리)
-- 10. PK 네이밍 통일: item_id → daily_recommendation_item_id,
--     step_id → task_step_id, session_id → task_session_id,
--     notification_id → notification_setting_id
-- 11. repeat_rule.frequency ENUM 값을 명세 기준으로 확정: WEEKLY / MONTHLY / YEARLY
--     (명세 2.14.6: 매주·매월·매년. DAILY 없음)
-- 12. repeat_rule.days_of_month 표현 규칙: 콤마 구분 + 마지막날은 'L'
--     예: '1,15,L' (1일, 15일, 매월 마지막날) ← 프론트와 파싱 규칙 공유 필요
-- 13. 전 테이블 감사 컬럼 통일          : 모든 테이블에 created_at/updated_at 포함
--     (BaseEntity(@MappedSuperclass) 전체 상속 전제. playlist_item.added_at → created_at,
--      search_history.searched_at → created_at 으로 통일)
-- 14. refresh_token 테이블 신규        : JWT 리프레시 토큰 저장
--     (재발급 검증 + 로그아웃/탈취 시 무효화용, 명세 4.2 로그아웃 지원.
--      기능명세에 없는 기술 요구사항이며, Redis 미도입 환경이라 DB 저장 채택)
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- user
-- ------------------------------------------------------------
CREATE TABLE `user` (
                        user_id               BIGINT       NOT NULL AUTO_INCREMENT,
                        social_provider       VARCHAR(20)  NOT NULL COMMENT 'KAKAO / GOOGLE',
                        social_id             VARCHAR(255) NOT NULL,
                        nickname              VARCHAR(100) NOT NULL,
                        email                 VARCHAR(255) NULL,
                        profile_image_url     VARCHAR(500) NULL,
                        timetable_start_time  TIME         NOT NULL DEFAULT '08:00:00' COMMENT '[추가] 시간표 표시 시작 (명세 4.6.1)',
                        timetable_end_time    TIME         NOT NULL DEFAULT '24:00:00' COMMENT '[추가] 시간표 표시 종료 (명세 4.6.1)',
                        created_at            DATETIME     NOT NULL,
                        updated_at            DATETIME     NOT NULL,
                        PRIMARY KEY (user_id),
                        UNIQUE KEY uk_user_social (social_provider, social_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
-- [삭제] recommended_daily_time : daily_recommendation.total_recommended_minutes로 일원화

-- ------------------------------------------------------------
-- category
-- ------------------------------------------------------------
CREATE TABLE category (
                          category_id  BIGINT       NOT NULL AUTO_INCREMENT,
                          user_id      BIGINT       NOT NULL,
                          name         VARCHAR(100) NOT NULL,
                          color        VARCHAR(20)  NOT NULL COMMENT '동일 사용자 내 색상 중복 불가 (명세 2.12.3) - 애플리케이션 검증',
                          sort_order   INT          NOT NULL DEFAULT 0,
                          is_default   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '기본 카테고리(학업/일상/기념일)',
                          is_deleted   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '[추가] soft delete - 완료 과업 카테고리 3년 보존 (명세 4.3.1)',
                          created_at   DATETIME     NOT NULL,
                          updated_at   DATETIME     NOT NULL,
                          PRIMARY KEY (category_id),
                          KEY idx_category_user (user_id),
                          CONSTRAINT fk_category_user FOREIGN KEY (user_id) REFERENCES `user` (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- task
-- ------------------------------------------------------------
CREATE TABLE task (
                      task_id          BIGINT       NOT NULL AUTO_INCREMENT,
                      user_id          BIGINT       NOT NULL,
                      category_id      BIGINT       NOT NULL,
                      similar_task_id  BIGINT       NULL COMMENT '[추가] 등록 시 선택한 비슷한 과거 과업 (명세 2.16, 예상시간 보정 로직 입력값)',
                      title            VARCHAR(255) NOT NULL,
                      priority         ENUM('HIGH','MEDIUM','LOW') NULL COMMENT '우선순위 상/중/하 - 추천 로직이 계산, 계산 전 NULL',
                      is_starred       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '중요 표시 (명세 4.4)',
                      deadline         DATE         NOT NULL COMMENT '마감일 - 등록 시 필수 (명세 2.10.7)',
                      estimated_time   INT          NULL COMMENT '남은 예상 시간(분) - 보정 로직이 갱신',
                      status           ENUM('TODO','IN_PROGRESS','DONE') NOT NULL DEFAULT 'TODO',
                      is_deleted       TINYINT(1)   NOT NULL DEFAULT 0,
                      created_at       DATETIME     NOT NULL,
                      updated_at       DATETIME     NOT NULL,
                      PRIMARY KEY (task_id),
                      KEY idx_task_user_status (user_id, status),
                      KEY idx_task_deadline (deadline),
                      CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES `user` (user_id),
                      CONSTRAINT fk_task_category FOREIGN KEY (category_id) REFERENCES category (category_id),
                      CONSTRAINT fk_task_similar FOREIGN KEY (similar_task_id) REFERENCES task (task_id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
-- [삭제] repeat_type : repeat_rule 존재 여부 + frequency가 단일 소스 (중복 어긋남 방지)

-- ------------------------------------------------------------
-- task_step (과업 세부 단계 - 명세 2.9.4)
-- ------------------------------------------------------------
CREATE TABLE task_step (
                           task_step_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '[수정] step_id → task_step_id (PK 네이밍 통일)',
                           task_id        BIGINT       NOT NULL,
                           title          VARCHAR(100) NOT NULL COMMENT '기본 플레이스홀더: 개념 정리/문제 풀이/전체 복습하기',
                           progress_rate  INT          NOT NULL DEFAULT 0 COMMENT '0~100',
                           sort_order     INT          NOT NULL DEFAULT 0,
                           created_at     DATETIME     NOT NULL,
                           updated_at     DATETIME     NOT NULL,
                           PRIMARY KEY (task_step_id),
                           KEY idx_task_step_task (task_id),
                           CONSTRAINT fk_task_step_task FOREIGN KEY (task_id) REFERENCES task (task_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- repeat_rule (반복 설정 - 명세 2.14.5~2.14.9)
-- ------------------------------------------------------------
CREATE TABLE repeat_rule (
                             repeat_rule_id  BIGINT       NOT NULL AUTO_INCREMENT,
                             task_id         BIGINT       NOT NULL,
                             frequency       ENUM('WEEKLY','MONTHLY','YEARLY') NOT NULL COMMENT '[수정] 명세 2.14.6 기준 확정 (매주/매월/매년)',
                             days_of_week    VARCHAR(50)  NULL COMMENT 'WEEKLY용, 콤마 구분 0(일)~6(토) 예: 0,3,6',
                             days_of_month   VARCHAR(100) NULL COMMENT 'MONTHLY/YEARLY용, 콤마 구분 + 마지막날 L  예: 1,15,L',
                             month_of_year   INT          NULL COMMENT 'YEARLY용 (월 단일 선택, 명세 2.14.9)',
                             start_date      DATE         NOT NULL,
                             end_date        DATE         NULL,
                             created_at      DATETIME     NOT NULL,
                             updated_at      DATETIME     NOT NULL,
                             PRIMARY KEY (repeat_rule_id),
    -- [추가] 과업당 반복 규칙 1개
                             UNIQUE KEY uk_repeat_rule_task (task_id),
                             CONSTRAINT fk_repeat_rule_task FOREIGN KEY (task_id) REFERENCES task (task_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
-- 참고: v1의 days_of_year 컬럼은 month_of_year + days_of_month 조합으로 표현 가능하여 제외
--       (YEARLY = 선택한 월(month_of_year)의 선택한 일들(days_of_month)에 반복)

-- ------------------------------------------------------------
-- playlist
-- ------------------------------------------------------------
CREATE TABLE playlist (
                          playlist_id  BIGINT   NOT NULL AUTO_INCREMENT,
                          user_id      BIGINT   NOT NULL,
                          created_at   DATETIME NOT NULL,
                          updated_at   DATETIME NOT NULL,
                          PRIMARY KEY (playlist_id),
    -- 사용자당 플레이리스트 1개
                          UNIQUE KEY uk_playlist_user (user_id),
                          CONSTRAINT fk_playlist_user FOREIGN KEY (user_id) REFERENCES `user` (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- playlist_item
-- ------------------------------------------------------------
CREATE TABLE playlist_item (
                               playlist_item_id  BIGINT      NOT NULL AUTO_INCREMENT,
                               playlist_id       BIGINT      NOT NULL,
                               task_id           BIGINT      NOT NULL,
                               sort_order        INT         NOT NULL DEFAULT 0 COMMENT '드래그로 순서 변경 (명세 2.8.5)',
                               source_mode       VARCHAR(30) NULL COMMENT '[추가] 추가 경로: FIRE/BALANCE/TASTE/CLEAR 등 모드명, 수동 추가 시 NULL (명세 2.7.4, 2.8.5)',
                               created_at        DATETIME    NOT NULL,
                               updated_at        DATETIME    NOT NULL,
                               PRIMARY KEY (playlist_item_id),
    -- 같은 과업 중복 추가 방지
                               UNIQUE KEY uk_playlist_item (playlist_id, task_id),
                               CONSTRAINT fk_playlist_item_playlist FOREIGN KEY (playlist_id) REFERENCES playlist (playlist_id) ON DELETE CASCADE,
                               CONSTRAINT fk_playlist_item_task FOREIGN KEY (task_id) REFERENCES task (task_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- task_session (플레이/타이머 세션 - 명세 2.8)
-- ------------------------------------------------------------
CREATE TABLE task_session (
                              task_session_id  BIGINT   NOT NULL AUTO_INCREMENT COMMENT '[수정] session_id → task_session_id (PK 네이밍 통일)',
                              task_id          BIGINT   NOT NULL,
                              user_id          BIGINT   NOT NULL,
                              started_at       DATETIME NOT NULL,
                              ended_at         DATETIME NULL,
                              elapsed_time     INT      NOT NULL DEFAULT 0 COMMENT '누적 소요 시간(초)',
                              status           ENUM('PLAYING','PAUSED','DONE') NOT NULL DEFAULT 'PAUSED' COMMENT '최초 진입은 중지 상태 (명세 2.4.1)',
                              created_at       DATETIME NOT NULL,
                              updated_at       DATETIME NOT NULL,
                              PRIMARY KEY (task_session_id),
                              KEY idx_task_session_task (task_id),
    -- 통계 일별 조회용
                              KEY idx_task_session_user_started (user_id, started_at),
                              CONSTRAINT fk_task_session_task FOREIGN KEY (task_id) REFERENCES task (task_id),
                              CONSTRAINT fk_task_session_user FOREIGN KEY (user_id) REFERENCES `user` (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- feedback (피드백 - 명세 2.9)
-- ------------------------------------------------------------
CREATE TABLE feedback (
                          feedback_id      BIGINT     NOT NULL AUTO_INCREMENT,
                          task_session_id  BIGINT     NOT NULL,
                          task_id          BIGINT     NOT NULL,
                          user_id          BIGINT     NOT NULL,
                          progress_rate    INT        NULL COMMENT '0~100, 메모만 작성 가능하므로 NULL 허용 (명세 2.9.2)',
                          memo             TEXT       NULL,
                          is_completed     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '진척도 100% 완료 여부',
                          is_draft         TINYINT(1) NOT NULL DEFAULT 0 COMMENT '[추가] 임시저장 여부 (명세 2.9.8) - 통계/보정 계산에서 draft 제외',
                          created_at       DATETIME   NOT NULL,
                          updated_at       DATETIME   NOT NULL,
                          PRIMARY KEY (feedback_id),
                          KEY idx_feedback_task (task_id),
                          KEY idx_feedback_user_created (user_id, created_at),
                          CONSTRAINT fk_feedback_session FOREIGN KEY (task_session_id) REFERENCES task_session (task_session_id),
                          CONSTRAINT fk_feedback_task FOREIGN KEY (task_id) REFERENCES task (task_id),
                          CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES `user` (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- timetable (시간표 블록 - 명세 4.5)
-- ------------------------------------------------------------
CREATE TABLE timetable (
                           timetable_id  BIGINT       NOT NULL AUTO_INCREMENT,
                           user_id       BIGINT       NOT NULL,
                           day_of_week   TINYINT      NOT NULL COMMENT '0(일)~6(토)',
                           start_time    TIME         NOT NULL,
                           end_time      TIME         NOT NULL,
                           title         VARCHAR(100) NOT NULL,
                           place         VARCHAR(100) NULL,
                           color         VARCHAR(20)  NOT NULL,
                           created_at    DATETIME     NOT NULL,
                           updated_at    DATETIME     NOT NULL,
                           PRIMARY KEY (timetable_id),
                           KEY idx_timetable_user_day (user_id, day_of_week),
                           CONSTRAINT fk_timetable_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
-- 참고: 시간 겹침 검증(명세 4.5.8)은 애플리케이션에서 처리

-- ------------------------------------------------------------
-- notification_setting (과업별 알림 - 명세 2.13)
-- ------------------------------------------------------------
CREATE TABLE notification_setting (
                                      notification_setting_id  BIGINT     NOT NULL AUTO_INCREMENT COMMENT '[수정] notification_id → notification_setting_id (PK 네이밍 통일)',
                                      task_id                  BIGINT     NOT NULL,
                                      notify_before            INT        NOT NULL DEFAULT 1440 COMMENT '알림 시점(분 단위), 기본 1일 전=1440 (명세 2.10.3)',
                                      is_enabled               TINYINT(1) NOT NULL DEFAULT 1,
                                      created_at               DATETIME   NOT NULL,
                                      updated_at               DATETIME   NOT NULL,
                                      PRIMARY KEY (notification_setting_id),
    -- 알림 단일 선택 (명세 2.13.1)
                                      UNIQUE KEY uk_notification_task (task_id),
                                      CONSTRAINT fk_notification_task FOREIGN KEY (task_id) REFERENCES task (task_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- user_notification_setting (사용자 알림 설정 - 명세 4.8)
-- ------------------------------------------------------------
CREATE TABLE user_notification_setting (
                                           user_notification_setting_id  BIGINT     NOT NULL AUTO_INCREMENT,
                                           user_id                       BIGINT     NOT NULL,
                                           is_service_alarm_enabled      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '최초 OFF (명세 4.8.1)',
                                           is_30min_pack_alarm_enabled   TINYINT(1) NOT NULL DEFAULT 0,
                                           created_at                    DATETIME   NOT NULL,
                                           updated_at                    DATETIME   NOT NULL,
                                           PRIMARY KEY (user_notification_setting_id),
    -- 사용자당 설정 1행
                                           UNIQUE KEY uk_user_notification_user (user_id),
                                           CONSTRAINT fk_user_notification_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- daily_recommendation (일별 추천 헤더 - 로직문서 4·5번)
-- ------------------------------------------------------------
CREATE TABLE daily_recommendation (
                                      daily_recommendation_id    BIGINT      NOT NULL AUTO_INCREMENT,
                                      user_id                    BIGINT      NOT NULL,
                                      recommended_date           DATE        NOT NULL,
                                      mode                       VARCHAR(30) NOT NULL COMMENT 'FIRE(불끄기)/BALANCE(밸런스)/TASTE(찍먹)/CLEAR(해치우기)/PACK30(30분팩)',
                                      available_minutes          INT         NULL COMMENT '오늘 가용 시간(분)',
                                      total_recommended_minutes  INT         NOT NULL COMMENT '오늘의 권장 시간(분) - 로직문서 5번',
                                      created_at                 DATETIME    NOT NULL,
                                      updated_at                 DATETIME    NOT NULL,
                                      PRIMARY KEY (daily_recommendation_id),
                                      KEY idx_daily_rec_user_date (user_id, recommended_date),
                                      CONSTRAINT fk_daily_rec_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- daily_recommendation_item (추천 상세)
-- ------------------------------------------------------------
CREATE TABLE daily_recommendation_item (
                                           daily_recommendation_item_id  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '[수정] item_id → 테이블명_id (PK 네이밍 통일)',
                                           daily_recommendation_id       BIGINT        NOT NULL,
                                           task_id                       BIGINT        NOT NULL,
                                           rank_order                    INT           NOT NULL,
                                           score                         DECIMAL(8,2)  NULL COMMENT '우선순위 점수 = 기본순위*0.8 + 진행순위*0.2 (로직문서 1번)',
                                           recommended_minutes           INT           NULL,
                                           created_at                    DATETIME      NOT NULL,
                                           updated_at                    DATETIME      NOT NULL,
                                           PRIMARY KEY (daily_recommendation_item_id),
                                           KEY idx_daily_rec_item_rec (daily_recommendation_id),
                                           CONSTRAINT fk_daily_rec_item_rec FOREIGN KEY (daily_recommendation_id) REFERENCES daily_recommendation (daily_recommendation_id) ON DELETE CASCADE,
                                           CONSTRAINT fk_daily_rec_item_task FOREIGN KEY (task_id) REFERENCES task (task_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- search_history (최근 검색어 - 명세 2.5.1) [신규]
-- ------------------------------------------------------------
CREATE TABLE search_history (
                                search_history_id  BIGINT       NOT NULL AUTO_INCREMENT,
                                user_id            BIGINT       NOT NULL,
                                keyword            VARCHAR(100) NOT NULL COMMENT '검색 시각은 created_at 사용',
                                created_at         DATETIME     NOT NULL,
                                updated_at         DATETIME     NOT NULL,
                                PRIMARY KEY (search_history_id),
    -- 최근 5개 조회용
                                KEY idx_search_history_user (user_id, created_at DESC),
                                CONSTRAINT fk_search_history_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- refresh_token (JWT 리프레시 토큰 - 재발급 검증/로그아웃 무효화) [신규]
-- ------------------------------------------------------------
CREATE TABLE refresh_token (
                               refresh_token_id  BIGINT       NOT NULL AUTO_INCREMENT,
                               user_id           BIGINT       NOT NULL,
                               token             VARCHAR(512) NOT NULL COMMENT '토큰 원문 대신 SHA-256 해시 저장 권장 (DB 유출 대비)',
                               expires_at        DATETIME     NOT NULL COMMENT '만료 시각 - 스케줄러로 주기 삭제 필요 (Redis TTL 부재 보완)',
                               created_at        DATETIME     NOT NULL,
                               updated_at        DATETIME     NOT NULL,
                               PRIMARY KEY (refresh_token_id),
    -- 사용자당 유효 토큰 1개(단일 세션 정책) - 재로그인 시 upsert. 멀티 디바이스 허용 시 이 제약 제거 + device 컬럼 추가
                               UNIQUE KEY uk_refresh_token_user (user_id),
    -- 재발급 요청 시 토큰 값으로 조회
                               KEY idx_refresh_token_token (token(255)),
                               CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
package com.cotato.blankit.domain.feedback;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.feedback.dto.request.SessionStatusUpdateRequest;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.feedback.repository.DailyElapsedTimeRepository;
import com.cotato.blankit.domain.feedback.service.TaskSessionService;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:session-elapsed-time-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class TaskSessionElapsedTimeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @MockitoBean Clock clock;

    @Autowired private TaskSessionService taskSessionService;
    @Autowired private DailyElapsedTimeRepository dailyElapsedTimeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TaskRepository taskRepository;

    private User user;
    private Task task;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(SEOUL);
        user = userRepository.save(User.create(SocialProvider.KAKAO, "elapsed-user", "elapsed@example.com", "소요시간유저", null, 120));
        Category category = categoryRepository.save(Category.create(user, "학업", "#FF5C5C", "book", 0, true));
        task = taskRepository.save(Task.create(user, category, "테스트 과업", LocalDate.of(2026, 8, 31), null, 120));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private void setTime(LocalDateTime seoulTime) {
        when(clock.instant()).thenReturn(seoulTime.toInstant(ZoneOffset.ofHours(9)));
    }

    private Long newSession(LocalDateTime at) {
        setTime(at);
        return taskSessionService.startSession(user.getId(), task.getId()).taskSessionId();
    }

    private void play(Long sessionId, LocalDateTime at) {
        setTime(at);
        taskSessionService.updateSessionStatus(user.getId(), sessionId,
                new SessionStatusUpdateRequest(TaskSessionStatus.PLAYING, 0));
    }

    private void pause(Long sessionId, LocalDateTime at, int elapsedSeconds) {
        setTime(at);
        taskSessionService.updateSessionStatus(user.getId(), sessionId,
                new SessionStatusUpdateRequest(TaskSessionStatus.PAUSED, elapsedSeconds));
    }

    private void done(Long sessionId, LocalDateTime at, int elapsedSeconds) {
        setTime(at);
        taskSessionService.updateSessionStatus(user.getId(), sessionId,
                new SessionStatusUpdateRequest(TaskSessionStatus.DONE, elapsedSeconds));
    }

    private int elapsedOn(LocalDate date) {
        return (int) dailyElapsedTimeRepository.sumElapsedSecondsByUserIdAndDate(user.getId(), date);
    }

    // ── 날짜가 넘어가지 않는 경우 ──────────────────────────────────────────────

    @Nested
    @DisplayName("날짜가 넘어가지 않는 경우")
    class SameDay {

        private final LocalDate DAY = LocalDate.of(2026, 7, 13);

        @Test
        @DisplayName("단일 PLAYING 구간이 DONE되면 전체 소요 시간이 해당 날짜에 기록된다")
        void singleInterval_done_recorded() {
            // given: 09:00 PLAYING → 10:30 DONE = 5400초
            Long sessionId = newSession(DAY.atTime(9, 0));
            play(sessionId, DAY.atTime(9, 0));
            // when
            done(sessionId, DAY.atTime(10, 30), 5400);
            // then
            assertThat(elapsedOn(DAY)).isEqualTo(5400);
        }

        @Test
        @DisplayName("PLAYING → PAUSED → PLAYING → DONE: 두 구간의 합산만 날짜에 기록된다")
        void twoIntervals_sumRecorded() {
            // given: 09:00~09:30(1800s) + 10:00~11:00(3600s) = 5400s
            Long sessionId = newSession(DAY.atTime(9, 0));
            play(sessionId, DAY.atTime(9, 0));
            pause(sessionId, DAY.atTime(9, 30), 1800);
            play(sessionId, DAY.atTime(10, 0));
            // when
            done(sessionId, DAY.atTime(11, 0), 5400);
            // then
            assertThat(elapsedOn(DAY)).isEqualTo(5400);
        }

        @Test
        @DisplayName("같은 날 두 세션의 소요 시간이 DailyElapsedTime에 누적된다")
        void twoSessions_accumulated() {
            // given: 세션1 1800s, 세션2 1800s → 합산 3600s
            Long session1 = newSession(DAY.atTime(9, 0));
            play(session1, DAY.atTime(9, 0));
            done(session1, DAY.atTime(9, 30), 1800);

            Long session2 = newSession(DAY.atTime(10, 0));
            play(session2, DAY.atTime(10, 0));
            // when
            done(session2, DAY.atTime(10, 30), 1800);
            // then
            assertThat(elapsedOn(DAY)).isEqualTo(3600);
        }

        @Test
        @DisplayName("PAUSED 상태에서도 DailyElapsedTime이 즉시 반영된다")
        void paused_reflectedImmediately() {
            Long sessionId = newSession(DAY.atTime(9, 0));
            play(sessionId, DAY.atTime(9, 0));
            pause(sessionId, DAY.atTime(9, 30), 1800);
            assertThat(elapsedOn(DAY)).isEqualTo(1800);
        }

        @Test
        @DisplayName("PAUSED 후 재개해 DONE하면 전체 구간 합산이 최종값으로 덮어써진다")
        void paused_thenDone_overwritesWithFinalSum() {
            // PAUSE 시점: 1800s 반영, DONE 시점: 5400s로 덮어씀
            Long sessionId = newSession(DAY.atTime(9, 0));
            play(sessionId, DAY.atTime(9, 0));
            pause(sessionId, DAY.atTime(9, 30), 1800);
            assertThat(elapsedOn(DAY)).isEqualTo(1800);
            play(sessionId, DAY.atTime(10, 0));
            done(sessionId, DAY.atTime(11, 0), 5400);
            assertThat(elapsedOn(DAY)).isEqualTo(5400);
        }

        @Test
        @DisplayName("다른 세션 PLAYING 중 새 세션 시작 시 기존 세션이 자동 PAUSED되고 시간이 즉시 반영된다")
        void autoPaused_whenOtherSessionStartsPlaying() {
            Task task2 = taskRepository.save(Task.create(user, categoryRepository.findAll().get(0), "다른 과업", DAY.plusDays(30), null, 60));
            // session1 PLAYING 09:00~09:30
            Long session1 = newSession(DAY.atTime(9, 0));
            play(session1, DAY.atTime(9, 0));
            // session2 PLAYING 시작 → session1 자동 PAUSED
            setTime(DAY.atTime(9, 30));
            Long session2 = taskSessionService.startSession(user.getId(), task2.getId()).taskSessionId();
            play(session2, DAY.atTime(9, 30));
            // session1의 1800s가 즉시 반영되어야 함
            assertThat(elapsedOn(DAY)).isEqualTo(1800);
        }
    }

    // ── 자정을 넘기는 연속 구간 ────────────────────────────────────────────────

    @Nested
    @DisplayName("자정을 넘기는 연속 구간")
    class CrossMidnight {

        private final LocalDate DAY1 = LocalDate.of(2026, 7, 13);
        private final LocalDate DAY2 = LocalDate.of(2026, 7, 14);

        @Test
        @DisplayName("자정을 넘기는 연속 구간은 날짜별로 소요 시간이 분리 기록된다")
        void crossMidnight_splitByDate() {
            // given: 07-13 23:00 → 07-14 01:00 (7200초)
            // day1: 23:00~00:00 = 3600s, day2: 00:00~01:00 = 3600s
            Long sessionId = newSession(DAY1.atTime(23, 0));
            play(sessionId, DAY1.atTime(23, 0));
            // when
            done(sessionId, DAY2.atTime(1, 0), 7200);
            // then
            assertThat(elapsedOn(DAY1)).isEqualTo(3600);
            assertThat(elapsedOn(DAY2)).isEqualTo(3600);
        }

        @Test
        @DisplayName("자정 직전 시작 → 자정 직후 DONE: 초 단위로 정확히 분리된다")
        void crossMidnight_boundary() {
            // given: 23:59:00 → 00:01:00 (120초)
            // day1: 23:59~00:00 = 60s, day2: 00:00~00:01 = 60s
            Long sessionId = newSession(DAY1.atTime(23, 59));
            play(sessionId, DAY1.atTime(23, 59));
            // when
            done(sessionId, DAY2.atTime(0, 1), 120);
            // then
            assertThat(elapsedOn(DAY1)).isEqualTo(60);
            assertThat(elapsedOn(DAY2)).isEqualTo(60);
        }

        @Test
        @DisplayName("두 날짜에 기록된 소요 시간의 합은 전체 구간 길이와 같다")
        void crossMidnight_totalSecondsConsistent() {
            // given: 07-13 22:00 → 07-14 02:00 (14400초)
            Long sessionId = newSession(DAY1.atTime(22, 0));
            play(sessionId, DAY1.atTime(22, 0));
            // when
            done(sessionId, DAY2.atTime(2, 0), 14400);
            // then
            assertThat(elapsedOn(DAY1) + elapsedOn(DAY2)).isEqualTo(14400);
        }
    }

    // ── 자정 전 PAUSE → 자정 후 PLAYING → DONE ────────────────────────────────

    @Nested
    @DisplayName("자정 전 PAUSE → 자정 후 PLAYING → DONE")
    class PauseBeforeMidnightPlayAfter {

        private final LocalDate DAY1 = LocalDate.of(2026, 7, 13);
        private final LocalDate DAY2 = LocalDate.of(2026, 7, 14);

        @Test
        @DisplayName("자정 전 구간은 day1에, 자정 후 구간은 day2에 각각 기록된다")
        void eachIntervalRecordedOnOwnDate() {
            // given:
            // Interval1: 07-13 23:00~23:30 = 1800s (day1)
            // Interval2: 07-14 00:30~01:00 = 1800s (day2)
            Long sessionId = newSession(DAY1.atTime(23, 0));
            play(sessionId, DAY1.atTime(23, 0));
            pause(sessionId, DAY1.atTime(23, 30), 1800);
            play(sessionId, DAY2.atTime(0, 30));
            // when
            done(sessionId, DAY2.atTime(1, 0), 3600);
            // then
            assertThat(elapsedOn(DAY1)).isEqualTo(1800);
            assertThat(elapsedOn(DAY2)).isEqualTo(1800);
        }

        @Test
        @DisplayName("PAUSE 구간(자정을 포함)은 어느 날짜에도 집계되지 않는다")
        void pausedPeriod_acrossMidnight_notCounted() {
            // given: PLAYING 30분 + PAUSED 60분(자정 포함) + PLAYING 30분 → 총 PLAYING 3600s
            // PAUSE 구간 23:30~00:30(60분)은 어디에도 집계 안 됨
            Long sessionId = newSession(DAY1.atTime(23, 0));
            play(sessionId, DAY1.atTime(23, 0));
            pause(sessionId, DAY1.atTime(23, 30), 1800);
            play(sessionId, DAY2.atTime(0, 30));
            // when
            done(sessionId, DAY2.atTime(1, 0), 3600);
            // then: day1 + day2 = 3600s (PAUSE 3600s 미포함)
            assertThat(elapsedOn(DAY1) + elapsedOn(DAY2)).isEqualTo(3600);
        }

        @Test
        @DisplayName("자정 전 PAUSE 후 다음날 재개 없이 다른 날 DONE: PAUSE 이전 구간만 day1에 기록된다")
        void pauseBeforeMidnight_resumeAndDoneNextDay() {
            // given: Interval1: 23:00~23:30 (day1 1800s)
            //        Interval2: 01:00~01:30 (day2 1800s)
            Long sessionId = newSession(DAY1.atTime(23, 0));
            play(sessionId, DAY1.atTime(23, 0));
            pause(sessionId, DAY1.atTime(23, 30), 1800);
            play(sessionId, DAY2.atTime(1, 0));
            // when
            done(sessionId, DAY2.atTime(1, 30), 3600);
            // then
            assertThat(elapsedOn(DAY1)).isEqualTo(1800);
            assertThat(elapsedOn(DAY2)).isEqualTo(1800);
        }
    }

    // ── 여러 날에 걸친 세션 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("여러 날에 걸친 세션")
    class MultiDay {

        @Test
        @DisplayName("3일에 걸친 세션의 소요 시간이 날짜별로 정확히 분리 기록된다")
        void threeDaySession_splitCorrectly() {
            // given:
            // Interval1: 07-12 23:30 → 07-13 00:30 (자정 경계)
            //   day1(07-12): 23:30~00:00 = 1800s
            //   day2(07-13): 00:00~00:30 = 1800s
            // Interval2: 07-13 22:00 → 07-14 02:00 (자정 경계)
            //   day2(07-13): 22:00~00:00 = 7200s
            //   day3(07-14): 00:00~02:00 = 7200s
            LocalDate day1 = LocalDate.of(2026, 7, 12);
            LocalDate day2 = LocalDate.of(2026, 7, 13);
            LocalDate day3 = LocalDate.of(2026, 7, 14);

            Long sessionId = newSession(day1.atTime(23, 30));
            play(sessionId, day1.atTime(23, 30));
            pause(sessionId, day2.atTime(0, 30), 3600);
            play(sessionId, day2.atTime(22, 0));
            // when
            done(sessionId, day3.atTime(2, 0), 18000);
            // then
            assertThat(elapsedOn(day1)).isEqualTo(1800);
            assertThat(elapsedOn(day2)).isEqualTo(1800 + 7200); // 두 구간 합산
            assertThat(elapsedOn(day3)).isEqualTo(7200);
        }

        @Test
        @DisplayName("3일에 걸친 전체 소요 시간 합산이 실제 PLAYING 구간 총합과 일치한다")
        void threeDaySession_totalConsistent() {
            // given: 총 PLAYING = 3600(Interval1) + 14400(Interval2) = 18000s
            LocalDate day1 = LocalDate.of(2026, 7, 12);
            LocalDate day2 = LocalDate.of(2026, 7, 13);
            LocalDate day3 = LocalDate.of(2026, 7, 14);

            Long sessionId = newSession(day1.atTime(23, 30));
            play(sessionId, day1.atTime(23, 30));
            pause(sessionId, day2.atTime(0, 30), 3600);
            play(sessionId, day2.atTime(22, 0));
            // when
            done(sessionId, day3.atTime(2, 0), 18000);
            // then
            int total = elapsedOn(day1) + elapsedOn(day2) + elapsedOn(day3);
            assertThat(total).isEqualTo(18000);
        }
    }
}

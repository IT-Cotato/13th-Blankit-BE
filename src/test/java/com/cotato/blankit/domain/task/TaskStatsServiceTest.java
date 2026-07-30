package com.cotato.blankit.domain.task;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.feedback.entity.DailyElapsedTime;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.feedback.repository.DailyElapsedTimeRepository;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.task.dto.response.TaskCalendarResponse;
import com.cotato.blankit.domain.task.dto.response.TaskDailyStatsResponse;
import com.cotato.blankit.domain.task.dto.response.TaskMonthlyStatsResponse;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.task.service.TaskStatsService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-stats-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class TaskStatsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-15T00:00:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @MockitoBean
    private Clock clock;

    @Autowired private TaskStatsService taskStatsService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskSessionRepository taskSessionRepository;
    @Autowired private DailyElapsedTimeRepository dailyElapsedTimeRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @PersistenceContext private EntityManager entityManager;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED_INSTANT);
        lenient().when(clock.getZone()).thenReturn(SEOUL);
        user = userRepository.save(User.create(SocialProvider.KAKAO, "stats-user", "stats@example.com", "통계유저", null, 120));
        category = categoryRepository.save(Category.create(user, "학업", "#FF5C5C", "book", 0, true));
    }

    private Task task(String title, LocalDate deadline, Integer estimatedTime) {
        return taskRepository.save(Task.create(user, category, title, deadline, null, estimatedTime));
    }

    private TaskSession session(Task task, LocalDate date, int elapsedSeconds) {
        LocalDateTime startedAt = date.atTime(9, 0);
        TaskSession saved = taskSessionRepository.save(
                TaskSession.create(task, user, startedAt, startedAt.plusSeconds(elapsedSeconds), elapsedSeconds, TaskSessionStatus.DONE)
        );
        dailyElapsedTimeRepository.findByUser_IdAndDate(user.getId(), date)
                .ifPresentOrElse(
                        record -> record.addElapsedSeconds(elapsedSeconds),
                        () -> dailyElapsedTimeRepository.save(DailyElapsedTime.create(user, date, elapsedSeconds))
                );
        return saved;
    }

    private Feedback submittedFeedback(TaskSession session, Task task, int progressRate) {
        return feedbackRepository.save(Feedback.create(session, task, user, progressRate, null, false));
    }

    private Feedback draftFeedback(TaskSession session, Task task) {
        return feedbackRepository.save(Feedback.create(session, task, user, 30, null, true));
    }

    // ── 캘린더 월별 과업 조회 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("캘린더 월별 과업 조회")
    class GetMonthlyCalendar {

        @Test
        @DisplayName("해당 월에 마감 과업이 없으면 빈 리스트를 반환한다")
        void getMonthlyCalendar_noTasks_returnsEmptyList() {
            // when
            List<TaskCalendarResponse> result = taskStatsService.getMonthlyCalendar(user.getId(), 2026, 7);
            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("서로 다른 날짜의 과업을 마감일 오름차순으로 반환한다")
        void getMonthlyCalendar_tasksOnDifferentDates_sortedByDate() {
            // given
            task("후반", LocalDate.of(2026, 7, 20), 60);
            task("초반", LocalDate.of(2026, 7, 5),  60);
            // when
            List<TaskCalendarResponse> result = taskStatsService.getMonthlyCalendar(user.getId(), 2026, 7);
            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 5));
            assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 7, 20));
        }

        @Test
        @DisplayName("같은 날짜의 여러 과업은 하나의 그룹으로 묶인다")
        void getMonthlyCalendar_multipleTasksSameDate_groupedIntoOneEntry() {
            // given
            task("A", LocalDate.of(2026, 7, 10), 60);
            task("B", LocalDate.of(2026, 7, 10), 60);
            // when
            List<TaskCalendarResponse> result = taskStatsService.getMonthlyCalendar(user.getId(), 2026, 7);
            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).tasks()).hasSize(2);
        }

        @Test
        @DisplayName("해당 월 범위 밖의 과업은 포함되지 않는다")
        void getMonthlyCalendar_tasksOutsideMonth_excluded() {
            // given
            Task thisMonth = task("이번달", LocalDate.of(2026, 7, 15), 60);
            task("지난달", LocalDate.of(2026, 6, 30), 60);
            task("다음달", LocalDate.of(2026, 8, 1),  60);
            // when
            List<TaskCalendarResponse> result = taskStatsService.getMonthlyCalendar(user.getId(), 2026, 7);
            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).tasks().get(0).taskId()).isEqualTo(thisMonth.getId());
        }
    }

    // ── 일별 통계 조회 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("일별 통계 조회")
    class GetDailyStats {

        @Test
        @DisplayName("미래 날짜이면 totalElapsedSeconds가 null이고 feedbackTasks가 빈 리스트다")
        void getDailyStats_futureDate_nullElapsedAndEmptyFeedbacks() {
            // given
            LocalDate tomorrow = TODAY.plusDays(1);
            // when
            TaskDailyStatsResponse result = taskStatsService.getDailyStats(user.getId(), tomorrow);
            // then
            assertThat(result.totalElapsedSeconds()).isNull();
            assertThat(result.feedbackTasks()).isEmpty();
        }

        @Test
        @DisplayName("오늘 세션이 여러 개면 elapsedTime을 합산하여 반환한다")
        void getDailyStats_multipleSessions_aggregatesElapsedSeconds() {
            // given
            Task taskA = task("A", TODAY.plusDays(3), 60);
            Task taskB = task("B", TODAY.plusDays(5), 60);
            session(taskA, TODAY, 1800);
            session(taskB, TODAY, 3600);
            entityManager.flush();
            // when
            TaskDailyStatsResponse result = taskStatsService.getDailyStats(user.getId(), TODAY);
            // then
            assertThat(result.totalElapsedSeconds()).isEqualTo(5400); // 1800 + 3600
        }

        @Test
        @DisplayName("오늘 세션이 없으면 totalElapsedSeconds가 0이다")
        void getDailyStats_noSessions_elapsedSecondsIsZero() {
            // when
            TaskDailyStatsResponse result = taskStatsService.getDailyStats(user.getId(), TODAY);
            // then
            assertThat(result.totalElapsedSeconds()).isEqualTo(0);
        }

        @Test
        @DisplayName("권장 시간은 estimatedTime ÷ 마감까지남은일수의 합산이다")
        void getDailyStats_recommendedMinutes_calculatedByFormula() {
            // given
            // taskA: 60분 / 5일 = 12분 기여
            task("A", TODAY.plusDays(5), 60);
            // taskB: 90분 / 1일 = 90분 기여  → 합산 102분
            task("B", TODAY.plusDays(1), 90);
            // when
            TaskDailyStatsResponse result = taskStatsService.getDailyStats(user.getId(), TODAY);
            // then
            assertThat(result.totalRecommendedMinutes()).isEqualTo(102);
        }

        @Test
        @DisplayName("마감일이 조회 날짜와 같은 과업은 권장 시간 계산에서 제외된다")
        void getDailyStats_taskDueOnQueryDate_excludedFromRecommended() {
            // given
            task("당일마감", TODAY, 60); // daysUntilDeadline = 0 → 제외
            // when
            TaskDailyStatsResponse result = taskStatsService.getDailyStats(user.getId(), TODAY);
            // then
            assertThat(result.totalRecommendedMinutes()).isEqualTo(0);
        }

        @Test
        @DisplayName("isDraft=true 피드백은 feedbackTasks에 포함되지 않는다")
        void getDailyStats_draftFeedback_notIncludedInFeedbackTasks() {
            // given
            Task taskA = task("A", TODAY.plusDays(3), 60);
            TaskSession s = session(taskA, TODAY, 1800);
            draftFeedback(s, taskA);
            entityManager.flush();
            // when
            TaskDailyStatsResponse result = taskStatsService.getDailyStats(user.getId(), TODAY);
            // then
            assertThat(result.feedbackTasks()).isEmpty();
        }

        @Test
        @DisplayName("isDraft=false 피드백은 feedbackTasks에 포함되며 과업 정보를 담는다")
        void getDailyStats_submittedFeedback_includedInFeedbackTasks() {
            // given
            Task taskA = task("기말고사 준비", TODAY.plusDays(3), 60);
            TaskSession s = session(taskA, TODAY, 1800);
            submittedFeedback(s, taskA, 40);
            entityManager.flush();
            // when
            TaskDailyStatsResponse result = taskStatsService.getDailyStats(user.getId(), TODAY);
            // then
            assertThat(result.feedbackTasks()).hasSize(1);
            TaskDailyStatsResponse.FeedbackTaskItem item = result.feedbackTasks().get(0);
            assertThat(item.taskId()).isEqualTo(taskA.getId());
            assertThat(item.title()).isEqualTo("기말고사 준비");
            assertThat(item.progressRate()).isEqualTo(40);
            assertThat(item.isCompleted()).isFalse();
        }
    }

    // ── 월별 통계 조회 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("월별 통계 조회")
    class GetMonthlyStats {

        @Test
        @DisplayName("월별 통계는 해당 월의 모든 날짜 항목을 반환한다")
        void getMonthlyStats_returnsAllDaysOfMonth() {
            // when
            TaskMonthlyStatsResponse result = taskStatsService.getMonthlyStats(user.getId(), 2026, 7);
            // then: 7월은 31일
            assertThat(result.dailyStats()).hasSize(31);
            assertThat(result.dailyStats().get(0).date()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(result.dailyStats().get(30).date()).isEqualTo(LocalDate.of(2026, 7, 31));
        }

        @Test
        @DisplayName("오늘 이후 날짜의 actualMinutes는 null이고 오늘은 null이 아니다")
        void getMonthlyStats_futureDays_actualMinutesNullTodayNonNull() {
            // when
            TaskMonthlyStatsResponse result = taskStatsService.getMonthlyStats(user.getId(), 2026, 7);
            // then: index 14 = 7월 15일(오늘), index 15 = 7월 16일(내일)
            assertThat(result.dailyStats().get(14).date()).isEqualTo(TODAY);
            assertThat(result.dailyStats().get(14).actualMinutes()).isNotNull();
            assertThat(result.dailyStats().get(15).date()).isEqualTo(TODAY.plusDays(1));
            assertThat(result.dailyStats().get(15).actualMinutes()).isNull();
        }

        @Test
        @DisplayName("과거 날짜의 actualMinutes는 해당 날 세션 elapsedTime 합산을 분으로 반환한다")
        void getMonthlyStats_pastDayWithSessions_actualMinutesComputed() {
            // given: 7월 10일 세션 2개 — 3600초 + 1800초 = 5400초 → 90분
            Task taskA = task("A", TODAY.plusDays(10), 120);
            session(taskA, LocalDate.of(2026, 7, 10), 3600);
            session(taskA, LocalDate.of(2026, 7, 10), 1800);
            entityManager.flush();
            // when
            TaskMonthlyStatsResponse result = taskStatsService.getMonthlyStats(user.getId(), 2026, 7);
            // then: index 9 = 7월 10일
            assertThat(result.dailyStats().get(9).date()).isEqualTo(LocalDate.of(2026, 7, 10));
            assertThat(result.dailyStats().get(9).actualMinutes()).isEqualTo(90);
        }

        @Test
        @DisplayName("세션이 없는 과거 날짜의 actualMinutes는 0이다")
        void getMonthlyStats_pastDayNoSessions_actualMinutesIsZero() {
            // when
            TaskMonthlyStatsResponse result = taskStatsService.getMonthlyStats(user.getId(), 2026, 7);
            // then: 7월 1일(index=0)은 세션 없음 → 0
            assertThat(result.dailyStats().get(0).actualMinutes()).isEqualTo(0);
        }

        @Test
        @DisplayName("날짜별 권장 시간은 해당 날짜 기준 마감까지 남은 일수로 계산되며 당일 마감은 0이다")
        void getMonthlyStats_recommendedMinutes_differsPerDayAndZeroOnDeadlineDay() {
            // given: estimatedTime=10, deadline=7월 20일
            // 7월 18일(index 17): 10 / 2 = 5분
            // 7월 19일(index 18): 10 / 1 = 10분
            // 7월 20일(index 19): daysUntilDeadline=0 → 제외 → 0분
            task("마감임박", LocalDate.of(2026, 7, 20), 10);
            // when
            TaskMonthlyStatsResponse result = taskStatsService.getMonthlyStats(user.getId(), 2026, 7);
            // then
            assertThat(result.dailyStats().get(17).recommendedMinutes()).isEqualTo(5);
            assertThat(result.dailyStats().get(18).recommendedMinutes()).isEqualTo(10);
            assertThat(result.dailyStats().get(19).recommendedMinutes()).isEqualTo(0);
        }
    }
}

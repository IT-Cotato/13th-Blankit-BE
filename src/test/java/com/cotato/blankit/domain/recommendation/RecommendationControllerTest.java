package com.cotato.blankit.domain.recommendation;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.support.AccessTokenTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:recommendation-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class RecommendationControllerTest {

    // FixedClock: today = 2026-07-24 (UTC)
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 24);

    private MockMvc mockMvc;
    private User user;
    private String token;
    private Category category;

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private TaskSessionRepository taskSessionRepository;
    @Autowired private AccessTokenTestFactory accessTokenTestFactory;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        user = userRepository.save(User.create(SocialProvider.KAKAO, "rec-user", "rec@example.com", "추천유저", null, 120));
        token = accessTokenTestFactory.createAccessToken(user.getId());
        category = categoryRepository.save(Category.create(user, "학업", "#5C9EFF", "book",0, true));
    }

    @Test
    void getTodayRecommendation_noTasks_returns0Minutes() throws Exception {
        // 활성 과업이 없으면 권장 시간 0분 반환
        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendedDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(0));
    }

    @Test
    void getTodayRecommendation_excludesDoneTask() throws Exception {
        // DONE 상태 과업은 권장 시간 계산에서 제외됨
        Task doneTask = taskRepository.save(Task.create(user, category, "완료 과업", TODAY.plusDays(5), null, 300));
        doneTask.updateStatus(TaskStatus.DONE);

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(0));
    }

    @Test
    void getTodayRecommendation_excludesNullEstimatedTime() throws Exception {
        // estimatedTime이 null인 과업은 권장 시간 계산에서 제외됨
        taskRepository.save(Task.create(user, category, "시간미입력 과업", TODAY.plusDays(5), null));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(0));
    }

    @Test
    void getTodayRecommendation_excludesPastDeadlineTask() throws Exception {
        // 마감일이 어제인 과업은 권장 시간 계산에서 제외됨
        taskRepository.save(Task.create(user, category, "마감 초과 과업", TODAY.minusDays(1), null, 120));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(0));
    }

    @Test
    void getTodayRecommendation_dueTodayTask_isExcluded() throws Exception {
        // 마감 당일 과업은 권장 시간 계산에서 제외됨
        taskRepository.save(Task.create(user, category, "오늘 마감 과업", TODAY, null, 60));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(0));
    }

    @Test
    void getTodayRecommendation_dueTodayTaskMixedWithOthers_excludesDueTodayOnly() throws Exception {
        // 마감 당일 과업은 제외되고, 내일 마감 과업만 계산에 포함됨
        taskRepository.save(Task.create(user, category, "오늘 마감 과업", TODAY, null, 60));
        taskRepository.save(Task.create(user, category, "내일 마감 과업", TODAY.plusDays(1), null, 30));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // 오늘 마감 과업(60분) 제외, 내일 마감 과업: round(30/1) = 30분
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(30));
    }

    @Test
    void getTodayRecommendation_taskDueTomorrow_noDivisionByZero() throws Exception {
        // 내일 마감: daysRemaining = DAYS.between(today, today+1) = 1 (최솟값)
        // 0으로 나누기가 발생하지 않음을 보장
        taskRepository.save(Task.create(user, category, "내일 마감 과업", TODAY.plusDays(1), null, 60));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(60));
    }

    @Test
    void getTodayRecommendation_multipleTasks_sumsCorrectly() throws Exception {
        // 과업A: estimatedTime=300분, deadline=2026-07-30 (daysRemaining=6) → 300/6=50.0
        // 과업B: estimatedTime=60분,  deadline=2026-07-25 (daysRemaining=1) → 60/1=60.0
        // 합계 = round(110.0) = 110분
        taskRepository.save(Task.create(user, category, "과업A", LocalDate.of(2026, 7, 30), null, 300));
        taskRepository.save(Task.create(user, category, "과업B", LocalDate.of(2026, 7, 25), null, 60));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(110));
    }

    @Test
    void getTodayRecommendation_nonIntegerSum_roundsHalfUp() throws Exception {
        // estimatedTime=10, deadline=today+3 → 10/3 = 3.333...
        // Math.round(3.333) = 3, Math.ceil(3.333) = 4 → 기대값 3으로 반올림 검증
        taskRepository.save(Task.create(user, category, "비정수 과업", TODAY.plusDays(3), null, 10));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(3));
    }

    // ─── GET /api/recommendations/all ────────────────────────────────────────

    @Test
    void getAllRecommendation_noTasks_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/recommendations/all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendedDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.tasks").isEmpty());
    }

    @Test
    void getAllRecommendation_fourTasks_returnsAllWithFields() throws Exception {
        // 마감 순으로 1위 과업이 결정되도록 단순 구성
        Task taskA = taskRepository.save(Task.create(user, category, "과업A", LocalDate.of(2026, 7, 25), null, 60));
        taskRepository.save(Task.create(user, category, "과업B", LocalDate.of(2026, 7, 26), null, 120));
        taskRepository.save(Task.create(user, category, "과업C", LocalDate.of(2026, 7, 28), null, 90));
        Task taskD = taskRepository.save(Task.create(user, category, "과업D", LocalDate.of(2026, 7, 30), null, 30));

        mockMvc.perform(get("/api/recommendations/all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks.length()").value(4))
                .andExpect(jsonPath("$.data.tasks[0].rankOrder").value(1))
                .andExpect(jsonPath("$.data.tasks[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.data.tasks[0].taskId").value(taskA.getId()))
                .andExpect(jsonPath("$.data.tasks[3].rankOrder").value(4))
                .andExpect(jsonPath("$.data.tasks[3].priority").value("LOW"))
                .andExpect(jsonPath("$.data.tasks[3].taskId").value(taskD.getId()));
    }

    @Test
    void getAllRecommendation_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/recommendations/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getThirtyMinutePackRecommendationReturnsCalculatedTasks() throws Exception {
        Task task = taskRepository.save(Task.create(
                user, category, "빠른 과업", TODAY.plusDays(1), null, 100));
        task.updateProgressRate(40);
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(task, user, LocalDateTime.now(), LocalDateTime.now(), 600, TaskSessionStatus.DONE));
        feedbackRepository.save(Feedback.create(session, task, user, 40, "52p까지 진행", false));

        mockMvc.perform(get("/api/recommendations/pack30")
                        .param("availableMinutes", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableMinutes").value(20))
                .andExpect(jsonPath("$.data.tasks[0].taskId").value(task.getId()))
                .andExpect(jsonPath("$.data.tasks[0].title").value("빠른 과업"))
                .andExpect(jsonPath("$.data.tasks[0].categoryName").value("학업"))
                .andExpect(jsonPath("$.data.tasks[0].categoryColor").value("#5C9EFF"))
                .andExpect(jsonPath("$.data.tasks[0].categoryIconKey").value("book"))
                .andExpect(jsonPath("$.data.tasks[0].currentProgressRate").value(40))
                .andExpect(jsonPath("$.data.tasks[0].progressPerMinute").value(0.6))
                .andExpect(jsonPath("$.data.tasks[0].expectedProgressIncrease").value(12.0))
                .andExpect(jsonPath("$.data.tasks[0].memo").value("52p까지 진행"));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 30})
    void getThirtyMinutePackRecommendationAcceptsRangeBoundaries(int availableMinutes) throws Exception {
        mockMvc.perform(get("/api/recommendations/pack30")
                        .param("availableMinutes", String.valueOf(availableMinutes))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableMinutes").value(availableMinutes));
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 31})
    void getThirtyMinutePackRecommendationRejectsOutOfRangeMinutes(int availableMinutes) throws Exception {
        mockMvc.perform(get("/api/recommendations/pack30")
                        .param("availableMinutes", String.valueOf(availableMinutes))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void getTodayRecommendation_mixedValidAndInvalid_sumsOnlyValid() throws Exception {
        // 유효 과업: estimatedTime=100분, deadline=today+4 (daysRemaining=4) → round(100/4)=25
        // 나머지(DONE/null/마감초과)는 제외 → 합계 25분
        taskRepository.save(Task.create(user, category, "유효 과업", TODAY.plusDays(4), null, 100));
        Task doneTask = taskRepository.save(Task.create(user, category, "DONE 과업", TODAY.plusDays(4), null, 200));
        doneTask.updateStatus(TaskStatus.DONE);
        taskRepository.save(Task.create(user, category, "null 과업", TODAY.plusDays(4), null));
        taskRepository.save(Task.create(user, category, "마감초과 과업", TODAY.minusDays(1), null, 100));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(25));
    }

    // ─── GET /api/recommendations/modes ────────────────────────────────────

    @Test
    void getRecommendationModes_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/recommendations/modes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRecommendationModes_noTasks_returns4EmptyModes() throws Exception {
        mockMvc.perform(get("/api/recommendations/modes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modes.length()").value(4))
                .andExpect(jsonPath("$.data.modes[0].mode").value("FIRE"))
                .andExpect(jsonPath("$.data.modes[0].tasks").isEmpty())
                .andExpect(jsonPath("$.data.modes[1].mode").value("BALANCE"))
                .andExpect(jsonPath("$.data.modes[2].mode").value("TASTE"))
                .andExpect(jsonPath("$.data.modes[3].mode").value("CLEAR"));
    }

    @Test
    void getRecommendationModes_withThreeTasks_fireContainsHighTask() throws Exception {
        // n=3: progress null → progress rank 동점(모두 0%) → urgency만으로 구분
        // rank1=HIGH(+1일), rank2=MEDIUM(+2일), rank3=LOW(+3일)
        // totalMinutes=round(60/1+30/2+60/3)=round(60+15+20)=95
        // FIRE: HIGH=high(est=60) < 95 → [high(min(60,95)=60)]
        Task high = taskRepository.save(Task.create(user, category, "HIGH과업", TODAY.plusDays(1), null, 60));
        taskRepository.save(Task.create(user, category, "MED과업", TODAY.plusDays(2), null, 30));
        taskRepository.save(Task.create(user, category, "LOW과업", TODAY.plusDays(3), null, 60));

        mockMvc.perform(get("/api/recommendations/modes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modes[0].mode").value("FIRE"))
                .andExpect(jsonPath("$.data.modes[0].tasks[0].taskId").value(high.getId()))
                .andExpect(jsonPath("$.data.modes[0].tasks[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.data.modes[0].tasks[0].recommendedMinutes").value(60));
    }

    @Test
    void getAllRecommendation_progressRateAppearsInResponse() throws Exception {
        // progressRate가 설정된 과업은 해당 값을, 미설정 과업은 null을 반환
        // rank1=+1일(urgency 높음), rank2=+2일
        Task withProgress = taskRepository.save(Task.create(user, category, "진행률 과업", TODAY.plusDays(1), null, 60));
        withProgress.updateProgressRate(50);
        taskRepository.save(Task.create(user, category, "진행률 없는 과업", TODAY.plusDays(2), null, 30));

        mockMvc.perform(get("/api/recommendations/all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[0].progressRate").value(50))
                .andExpect(jsonPath("$.data.tasks[1].progressRate").value((Object) null));
    }

    @Test
    void getTodayRecommendation_memoAppearsInTopTasks() throws Exception {
        // 최종 제출 피드백이 있는 과업: memo 포함
        Task taskWithMemo = taskRepository.save(Task.create(user, category, "메모 있는 과업", TODAY.plusDays(1), null, 60));
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskWithMemo, user, LocalDateTime.now(), LocalDateTime.now(), 600, TaskSessionStatus.DONE));
        feedbackRepository.save(Feedback.create(session, taskWithMemo, user, 50, "추천 피드백 메모", false));

        // 피드백 없는 과업: memo는 null
        taskRepository.save(Task.create(user, category, "피드백 없는 과업", TODAY.plusDays(2), null, 30));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topTasks[0].memo").value("추천 피드백 메모"))
                .andExpect(jsonPath("$.data.topTasks[1].memo").value((Object) null));
    }

    @Test
    void getAllRecommendation_memoAppearsInTasks() throws Exception {
        // 최종 제출 피드백이 있는 과업: memo 포함
        Task taskWithMemo = taskRepository.save(Task.create(user, category, "메모 있는 과업", TODAY.plusDays(1), null, 60));
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskWithMemo, user, LocalDateTime.now(), LocalDateTime.now(), 600, TaskSessionStatus.DONE));
        feedbackRepository.save(Feedback.create(session, taskWithMemo, user, 50, "전체 추천 메모", false));

        // 임시저장만 있는 과업: memo는 null
        Task taskDraftOnly = taskRepository.save(Task.create(user, category, "임시저장만 있는 과업", TODAY.plusDays(2), null, 30));
        TaskSession draftSession = taskSessionRepository.save(
                TaskSession.create(taskDraftOnly, user, LocalDateTime.now(), null, 0, TaskSessionStatus.PLAYING));
        feedbackRepository.save(Feedback.create(draftSession, taskDraftOnly, user, 20, "임시 메모", true));

        mockMvc.perform(get("/api/recommendations/all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[0].memo").value("전체 추천 메모"))
                .andExpect(jsonPath("$.data.tasks[1].memo").value((Object) null));
    }

    @Test
    void getRecommendationModes_memoAppearsInModeTasks() throws Exception {
        // HIGH 과업에 최종 제출 피드백이 있는 경우: FIRE 모드에 memo 포함
        Task highTask = taskRepository.save(Task.create(user, category, "HIGH과업", TODAY.plusDays(1), null, 60));
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(highTask, user, LocalDateTime.now(), LocalDateTime.now(), 600, TaskSessionStatus.DONE));
        feedbackRepository.save(Feedback.create(session, highTask, user, 50, "모드 피드백 메모", false));

        taskRepository.save(Task.create(user, category, "MED과업", TODAY.plusDays(2), null, 30));
        taskRepository.save(Task.create(user, category, "LOW과업", TODAY.plusDays(3), null, 60));

        mockMvc.perform(get("/api/recommendations/modes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modes[0].tasks[0].memo").value("모드 피드백 메모"));
    }

    @Test
    void getRecommendationModes_progressRateAppearsInResponse() throws Exception {
        // HIGH 과업에 progressRate를 설정하면 FIRE 모드 응답에 해당 값이 포함됨
        Task task = taskRepository.save(Task.create(user, category, "HIGH과업", TODAY.plusDays(1), null, 60));
        task.updateProgressRate(70);
        taskRepository.save(Task.create(user, category, "MED과업", TODAY.plusDays(2), null, 30));
        taskRepository.save(Task.create(user, category, "LOW과업", TODAY.plusDays(3), null, 60));

        mockMvc.perform(get("/api/recommendations/modes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modes[0].tasks[0].progressRate").value(70));
    }
}

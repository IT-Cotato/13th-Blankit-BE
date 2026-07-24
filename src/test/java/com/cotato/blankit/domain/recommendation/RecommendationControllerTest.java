package com.cotato.blankit.domain.recommendation;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    @Autowired private JwtTokenProvider jwtTokenProvider;

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
        token = jwtTokenProvider.createAccessToken(user.getId());
        category = categoryRepository.save(Category.create(user, "학업", "#5C9EFF", "book",0, true));
    }

    @Test
    void getTodayRecommendation_noTasks_returns0Minutes() throws Exception {
        // 활성 과업이 없으면 권장 시간 0분 반환
        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
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
    void getTodayRecommendation_dueTodayTask_daysRemainingIs1() throws Exception {
        // 당일 마감: daysRemaining = DAYS.between(today, today) + 1 = 1
        // estimatedTime=60분, daysRemaining=1 → ceil(60/1) = 60분
        taskRepository.save(Task.create(user, category, "오늘 마감 과업", TODAY, null, 60));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(60));
    }

    @Test
    void getTodayRecommendation_multipleTasks_sumsCorrectly() throws Exception {
        // 과업A: estimatedTime=300분, deadline=2026-07-30 (daysRemaining=7) → ceil(300/7)=43
        // 과업B: estimatedTime=60분,  deadline=2026-07-25 (daysRemaining=2) → ceil(60/2)=30
        // 합계 = 73분
        taskRepository.save(Task.create(user, category, "과업A", LocalDate.of(2026, 7, 30), null, 300));
        taskRepository.save(Task.create(user, category, "과업B", LocalDate.of(2026, 7, 25), null, 60));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(73));
    }

    @Test
    void getTodayRecommendation_mixedValidAndInvalid_sumsOnlyValid() throws Exception {
        // 유효 과업: estimatedTime=100분, deadline=today+4 (daysRemaining=5) → ceil(100/5)=20
        // 나머지(DONE/null/마감초과)는 제외 → 합계 20분
        taskRepository.save(Task.create(user, category, "유효 과업", TODAY.plusDays(4), null, 100));
        Task doneTask = taskRepository.save(Task.create(user, category, "DONE 과업", TODAY.plusDays(4), null, 200));
        doneTask.updateStatus(TaskStatus.DONE);
        taskRepository.save(Task.create(user, category, "null 과업", TODAY.plusDays(4), null));
        taskRepository.save(Task.create(user, category, "마감초과 과업", TODAY.minusDays(1), null, 100));

        mockMvc.perform(get("/api/recommendations/today")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecommendedMinutes").value(20));
    }
}

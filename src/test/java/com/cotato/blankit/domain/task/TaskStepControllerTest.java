package com.cotato.blankit.domain.task;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStep;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.task.repository.TaskStepRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.support.AccessTokenTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-step-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class TaskStepControllerTest {

    private MockMvc mockMvc;
    private User user;
    private User otherUser;
    private String token;
    private Task task;

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskStepRepository taskStepRepository;
    @Autowired private AccessTokenTestFactory accessTokenTestFactory;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        user = userRepository.save(User.create(SocialProvider.KAKAO, "step-user", "step@example.com", "단계유저", null, 120));
        otherUser = userRepository.save(User.create(SocialProvider.KAKAO, "step-other", "other-step@example.com", "타인", null, 120));
        Category category = categoryRepository.save(Category.create(user, "학업", "#5C9EFF", "book", 0, true));
        task = taskRepository.save(Task.create(user, category, "테스트 과업", LocalDate.of(2026, 12, 31), null));
        token = accessTokenTestFactory.createAccessToken(user.getId());
    }

    @Test
    @DisplayName("단계가 없으면 빈 목록을 반환한다")
    void getSteps_empty() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/{taskId}/steps", task.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("단계 목록이 taskStepId 오름차순으로 반환된다")
    void getSteps_returnsInInsertionOrder() throws Exception {
        taskStepRepository.save(TaskStep.create(task, "1단계"));
        taskStepRepository.save(TaskStep.create(task, "2단계"));
        taskStepRepository.save(TaskStep.create(task, "3단계"));

        mockMvc.perform(get("/api/v1/tasks/{taskId}/steps", task.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].title").value("1단계"))
                .andExpect(jsonPath("$.data[1].title").value("2단계"))
                .andExpect(jsonPath("$.data[2].title").value("3단계"));
    }

    @Test
    @DisplayName("다른 사용자의 과업에 단계를 조회하면 404를 반환한다")
    void getSteps_otherUserTask_notFound() throws Exception {
        Category otherCategory = categoryRepository.save(Category.create(otherUser, "타인학업", "#FFB85C", "book", 0, true));
        Task otherTask = taskRepository.save(Task.create(otherUser, otherCategory, "타인 과업", LocalDate.of(2026, 12, 31), null));

        mockMvc.perform(get("/api/v1/tasks/{taskId}/steps", otherTask.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    @DisplayName("단계를 생성하면 201과 생성된 단계를 반환한다")
    void createStep_success_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/{taskId}/steps", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "1단계 개념 정리" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("1단계 개념 정리"))
                .andExpect(jsonPath("$.data.progressRate").value(0))
                .andExpect(jsonPath("$.data.taskStepId").isNumber());
    }

    @Test
    @DisplayName("첫 단계를 생성하면 과업 진행률이 0으로 초기화된다")
    void createStep_firstStep_resetsTaskProgressRateToZero() throws Exception {
        task.updateProgressRate(70);

        mockMvc.perform(post("/api/v1/tasks/{taskId}/steps", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "첫 단계" }
                                """))
                .andExpect(status().isCreated());

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getProgressRate()).isEqualTo(0);
    }

    @Test
    @DisplayName("빈 제목으로 단계 생성을 시도하면 400을 반환한다")
    void createStep_blankTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/{taskId}/steps", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "   " }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("100자 초과 제목으로 단계 생성을 시도하면 400을 반환한다")
    void createStep_tooLongTitle_returns400() throws Exception {
        String longTitle = "a".repeat(101);

        mockMvc.perform(post("/api/v1/tasks/{taskId}/steps", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "%s" }
                                """.formatted(longTitle)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("단계 제목을 수정하면 200과 수정된 단계를 반환한다")
    void updateStep_title_success() throws Exception {
        TaskStep step = taskStepRepository.save(TaskStep.create(task, "수정 전"));

        mockMvc.perform(patch("/api/v1/tasks/{taskId}/steps/{stepId}", task.getId(), step.getTaskStepId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "수정 후" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정 후"))
                .andExpect(jsonPath("$.data.progressRate").value(0));
    }

    @Test
    @DisplayName("단계 진척도를 수정하면 과업 전체 진행률이 재계산된다")
    void updateStep_progressRate_recalculatesTaskProgress() throws Exception {
        TaskStep step1 = taskStepRepository.save(TaskStep.create(task, "1단계"));
        TaskStep step2 = taskStepRepository.save(TaskStep.create(task, "2단계"));

        mockMvc.perform(patch("/api/v1/tasks/{taskId}/steps/{stepId}", task.getId(), step1.getTaskStepId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "progressRate": 60 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressRate").value(60));

        // (60 + 0) / 2 = 30
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getProgressRate()).isEqualTo(30);
    }

    @Test
    @DisplayName("진척도 100 초과로 수정을 시도하면 400을 반환한다")
    void updateStep_progressRateOver100_returns400() throws Exception {
        TaskStep step = taskStepRepository.save(TaskStep.create(task, "단계"));

        mockMvc.perform(patch("/api/v1/tasks/{taskId}/steps/{stepId}", task.getId(), step.getTaskStepId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "progressRate": 101 }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 단계 수정을 시도하면 404를 반환한다")
    void updateStep_stepNotFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/tasks/{taskId}/steps/{stepId}", task.getId(), 99999L)
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "없는 단계" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_STEP_NOT_FOUND"));
    }

    @Test
    @DisplayName("단계를 삭제하면 200을 반환하고 DB에서 제거된다")
    void deleteStep_success() throws Exception {
        TaskStep step = taskStepRepository.save(TaskStep.create(task, "삭제 대상"));

        mockMvc.perform(delete("/api/v1/tasks/{taskId}/steps/{stepId}", task.getId(), step.getTaskStepId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(taskStepRepository.findById(step.getTaskStepId())).isEmpty();
    }

    @Test
    @DisplayName("마지막 단계를 삭제하면 과업 진행률이 0으로 초기화된다")
    void deleteStep_lastStep_resetsTaskProgressToZero() throws Exception {
        TaskStep step = taskStepRepository.save(TaskStep.create(task, "유일한 단계"));
        step.update(null, 80);
        task.updateProgressRate(80);

        mockMvc.perform(delete("/api/v1/tasks/{taskId}/steps/{stepId}", task.getId(), step.getTaskStepId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getProgressRate()).isEqualTo(0);
    }

    @Test
    @DisplayName("존재하지 않는 단계 삭제를 시도하면 404를 반환한다")
    void deleteStep_stepNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/{taskId}/steps/{stepId}", task.getId(), 99999L)
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_STEP_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 없이 단계 목록을 조회하면 401을 반환한다")
    void getSteps_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/{taskId}/steps", task.getId()))
                .andExpect(status().isUnauthorized());
    }
}

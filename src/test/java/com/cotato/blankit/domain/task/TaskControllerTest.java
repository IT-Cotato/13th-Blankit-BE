package com.cotato.blankit.domain.task;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.task.entity.NotificationSetting;
import com.cotato.blankit.domain.task.entity.RecurrenceType;
import com.cotato.blankit.domain.task.entity.RepeatMonthDays;
import com.cotato.blankit.domain.task.entity.RepeatRule;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.task.repository.NotificationSettingRepository;
import com.cotato.blankit.domain.task.repository.RepeatRuleRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.task.repository.TaskSessionRepository;
import com.cotato.blankit.domain.task.service.RepeatDeadlineRefreshService;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
        "spring.datasource.url=jdbc:h2:mem:task-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class TaskControllerTest {

    private MockMvc mockMvc;
    private User user;
    private User otherUser;
    private String token;
    private Category studyCategory;
    private Category workCategory;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private RepeatRuleRepository repeatRuleRepository;

    @Autowired
    private TaskSessionRepository taskSessionRepository;

    @Autowired
    private RepeatDeadlineRefreshService repeatDeadlineRefreshService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        user = userRepository.save(User.create(SocialProvider.KAKAO, "task-user", "user@example.com", "서윤", null, 120));
        otherUser = userRepository.save(User.create(SocialProvider.KAKAO, "other-task-user", "other@example.com", "다른사용자", null, 120));
        studyCategory = categoryRepository.save(Category.create(user, "학업", "#5C9EFF", 0, true));
        workCategory = categoryRepository.save(Category.create(user, "업무", "#5CFF8A", 1, false));
        categoryRepository.save(Category.create(otherUser, "학업", "#5C9EFF", 0, true));
        token = jwtTokenProvider.createAccessToken(user.getId());
    }

    @Test
    void formOptionsReturnsErdDefaults() throws Exception {
        mockMvc.perform(get("/api/tasks/form-options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultCategoryId").value(studyCategory.getId()))
                .andExpect(jsonPath("$.data.defaultReminderOffsetMinutes").value(1440))
                .andExpect(jsonPath("$.data.defaultRepeatEnabled").value(false))
                .andExpect(jsonPath("$.data.categories[0].color").value("#5C9EFF"))
                .andExpect(jsonPath("$.data.reminderOptions[0]").value(10))
                .andExpect(jsonPath("$.data.reminderOptions[1]").value(60))
                .andExpect(jsonPath("$.data.reminderOptions[2]").value(1440))
                .andExpect(jsonPath("$.data.reminderOptions[3]").value(4320))
                .andExpect(jsonPath("$.data.reminderOptions[4]").value(10080));
    }

    @Test
    void categoryCrudUsesIsDeletedAndAllowsDuplicateNames() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "학업",
                                  "color": "#FF5C5C"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.categoryName").value("학업"))
                .andExpect(jsonPath("$.data.color").value("#FF5C5C"));

        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "색상 중복",
                                  "color": "#FF5C5C"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_COLOR_ALREADY_USED"));

        String otherToken = jwtTokenProvider.createAccessToken(otherUser.getId());
        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "다른 사용자",
                                  "color": "#FF5C5C"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/categories/available-colors")
                        .header("Authorization", "Bearer " + token)
                        .param("editingCategoryId", String.valueOf(studyCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasItem("#5C9EFF")));

        Category customColorCategory = categoryRepository.save(Category.create(user, "커스텀", "#12AB34", 3, false));
        mockMvc.perform(get("/api/categories/available-colors")
                        .header("Authorization", "Bearer " + token)
                        .param("editingCategoryId", String.valueOf(customColorCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasItem("#12AB34")));

        mockMvc.perform(patch("/api/categories/{categoryId}", customColorCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "커스텀 수정",
                                  "color": "#12AB35"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("커스텀 수정"))
                .andExpect(jsonPath("$.data.color").value("#12AB35"));

        org.assertj.core.api.Assertions.assertThat(categoryRepository.findById(customColorCategory.getId()))
                .isPresent()
                .get()
                .satisfies(category -> {
                    org.assertj.core.api.Assertions.assertThat(category.getName()).isEqualTo("커스텀 수정");
                    org.assertj.core.api.Assertions.assertThat(category.getColor()).isEqualTo("#12AB35");
                });

        mockMvc.perform(patch("/api/categories/{categoryId}", customColorCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "color": "#5C9EFF"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_COLOR_ALREADY_USED"));

        Category otherOwnedCategory = categoryRepository.save(Category.create(otherUser, "타인수정", "#12AB34", 5, false));
        mockMvc.perform(patch("/api/categories/{categoryId}", otherOwnedCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "수정되면 안 됨",
                                  "color": "#123456"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        org.assertj.core.api.Assertions.assertThat(categoryRepository.findById(otherOwnedCategory.getId()))
                .isPresent()
                .get()
                .extracting(Category::getName)
                .isEqualTo("타인수정");

        categoryRepository.save(Category.create(user, "기념일", "#FFB85C", 4, false));
        mockMvc.perform(get("/api/categories/available-colors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value("#629EDA"));

        mockMvc.perform(delete("/api/categories/{categoryId}", workCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.categoryId == %d)]".formatted(workCategory.getId())).doesNotExist());
    }

    @Test
    void deleteCategoryFailsWhenCategoryHasTasks() throws Exception {
        taskRepository.save(Task.create(user, workCategory, "업무 과업", LocalDate.of(2026, 8, 12), null));

        mockMvc.perform(delete("/api/categories/{categoryId}", workCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        org.assertj.core.api.Assertions.assertThat(
                categoryRepository.findByIdAndUserIdAndDeletedFalse(workCategory.getId(), user.getId())
        ).isPresent();
    }

    @Test
    void defaultCategoriesAreCreatedOnceAndNotRecreatedAfterAllDeleted() throws Exception {
        User freshUser = userRepository.save(User.create(SocialProvider.KAKAO, "fresh-category-user", "fresh@example.com", "신규", null, 120));
        String freshToken = jwtTokenProvider.createAccessToken(freshUser.getId());

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        for (Category category : categoryRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscCreatedAtAscIdAsc(freshUser.getId())) {
            mockMvc.perform(delete("/api/categories/{categoryId}", category.getId())
                            .with(csrf())
                            .header("Authorization", "Bearer " + freshToken))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void createTaskStoresNotificationAndNoRepeatRuleByDefault() throws Exception {
        String response = mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "알고리즘 과제 제출",
                                  "deadline": "2026-08-12"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category.categoryId").value(studyCategory.getId()))
                .andExpect(jsonPath("$.data.priority").doesNotExist())
                .andExpect(jsonPath("$.data.starred").value(false))
                .andExpect(jsonPath("$.data.estimatedTime").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.deadline").value("2026-08-12"))
                .andExpect(jsonPath("$.data.notificationSetting.notifyBefore").value(1440))
                .andExpect(jsonPath("$.data.notificationSetting.enabled").value(true))
                .andExpect(jsonPath("$.data.repeatRule").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.data.taskId")).longValue();
        org.assertj.core.api.Assertions.assertThat(notificationSettingRepository.existsByTaskId(taskId)).isTrue();
        org.assertj.core.api.Assertions.assertThat(repeatRuleRepository.existsByTaskId(taskId)).isFalse();
    }

    @Test
    void createTaskStoresRequestedEstimatedTimeWhenSimilarTaskIsNull() throws Exception {
        String response = mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "직접 예상 시간",
                                  "deadline": "2026-08-12",
                                  "categoryId": %d,
                                  "estimatedTime": 90,
                                  "similarTaskId": null
                                }
                                """.formatted(studyCategory.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.estimatedTime").value(90))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.data.taskId")).longValue();
        org.assertj.core.api.Assertions.assertThat(taskRepository.findById(taskId))
                .isPresent()
                .get()
                .extracting(Task::getEstimatedTime)
                .isEqualTo(90);
    }

    @Test
    void createRepeatTaskCalculatesDeadlineAndAllowsNullEndDate() throws Exception {
        String response = mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "주간 회의",
                                  "categoryId": %d,
                                  "repeatRule": {
                                    "frequency": "WEEKLY",
                                    "daysOfWeek": [3],
                                    "startDate": "2026-06-03",
                                    "endDate": null
                                  }
                                }
                                """.formatted(studyCategory.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deadline").value("2026-06-03"))
                .andExpect(jsonPath("$.data.repeatRule.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data.repeatRule.endDate").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.data.taskId")).longValue();
        org.assertj.core.api.Assertions.assertThat(repeatRuleRepository.existsByTaskId(taskId)).isTrue();
    }

    @Test
    void createYearlyRepeatSupportsLastDayOfMonth() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "2월 말 정산",
                                  "categoryId": %d,
                                  "repeatRule": {
                                    "frequency": "YEARLY",
                                    "monthOfYear": 2,
                                    "daysOfMonth": [],
                                    "lastDayOfMonth": true,
                                    "startDate": "2026-01-01",
                                    "endDate": null
                                  }
                                }
                                """.formatted(studyCategory.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deadline").value("2027-02-28"))
                .andExpect(jsonPath("$.data.repeatRule.frequency").value("YEARLY"))
                .andExpect(jsonPath("$.data.repeatRule.monthOfYear").value(2))
                .andExpect(jsonPath("$.data.repeatRule.daysOfMonth.length()").value(0))
                .andExpect(jsonPath("$.data.repeatRule.lastDayOfMonth").value(true));
    }

    @Test
    void createTaskRejectsInvalidCategoryAndNotification() throws Exception {
        Category deletedCategory = categoryRepository.save(Category.create(user, "삭제", "#B55CFF", 2, false));
        deletedCategory.delete();

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "삭제 카테고리",
                                  "categoryId": %d,
                                  "deadline": "2026-08-12"
                                }
                                """.formatted(deletedCategory.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        Category otherCategory = categoryRepository.save(Category.create(otherUser, "타인", "#FFB85C", 1, false));
        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "타인 카테고리",
                                  "categoryId": %d,
                                  "deadline": "2026-08-12"
                                }
                                """.formatted(otherCategory.getId())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "알림 오류",
                                  "deadline": "2026-08-12",
                                  "notifyBefore": 9
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REMINDER_OFFSET"));

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "알림 선택지 오류",
                                  "deadline": "2026-08-12",
                                  "notifyBefore": 30
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REMINDER_OFFSET"));
    }

    @Test
    void repeatRuleCreateQueryAndValidationWork() throws Exception {
        String weeklyResponse = mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "주간 회의",
                                  "deadline": "2026-08-12",
                                  "categoryId": %d,
                                  "repeatRule": {
                                    "frequency": "WEEKLY",
                                    "daysOfWeek": [1, 3, 5],
                                    "startDate": "2026-08-12",
                                    "endDate": "2026-12-31"
                                  }
                                }
                                """.formatted(studyCategory.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.repeatRule.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data.repeatRule.daysOfWeek[0]").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long weeklyTaskId = ((Number) com.jayway.jsonpath.JsonPath.read(weeklyResponse, "$.data.taskId")).longValue();
        org.assertj.core.api.Assertions.assertThat(repeatRuleRepository.existsByTaskId(weeklyTaskId)).isTrue();

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-08-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "월말",
                                  "deadline": "2026-08-31",
                                  "categoryId": %d,
                                  "repeatRule": {
                                    "frequency": "MONTHLY",
                                    "daysOfMonth": [31],
                                    "lastDayOfMonth": true,
                                    "startDate": "2026-08-31",
                                    "endDate": "2026-10-31"
                                  }
                                }
                                """.formatted(studyCategory.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.repeatRule.lastDayOfMonth").value(true));

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.title == '월말')]").doesNotExist());

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "잘못된 요일",
                                  "deadline": "2026-08-12",
                                  "categoryId": %d,
                                  "repeatRule": {
                                    "frequency": "WEEKLY",
                                    "daysOfWeek": [7],
                                    "startDate": "2026-08-12",
                                    "endDate": "2026-12-31"
                                  }
                                }
                                """.formatted(studyCategory.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECURRENCE"));

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "널 요일",
                                  "categoryId": %d,
                                  "repeatRule": {
                                    "frequency": "WEEKLY",
                                    "daysOfWeek": [1, null],
                                    "startDate": "2026-08-12"
                                  }
                                }
                                """.formatted(studyCategory.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECURRENCE"));
    }

    @Test
    void yearlyRepeatSkipsNonexistentDates() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "윤년 반복",
                                  "deadline": "2028-02-29",
                                  "categoryId": %d,
                                  "repeatRule": {
                                    "frequency": "YEARLY",
                                    "monthOfYear": 2,
                                    "daysOfMonth": [29],
                                    "startDate": "2028-02-29",
                                    "endDate": "2029-03-01"
                                  }
                                }
                                """.formatted(studyCategory.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2029-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.title == '윤년 반복')]").doesNotExist());
    }

    @Test
    void repeatedTaskGenerationCreatesOccurrenceFromAnySourceStatusAndIsIdempotent() {
        Task repeatTask = taskRepository.save(Task.create(user, studyCategory, "지난 반복", LocalDate.parse("2026-05-25"), null));
        repeatRuleRepository.save(RepeatRule.create(
                repeatTask,
                RecurrenceType.WEEKLY,
                List.of(1),
                RepeatMonthDays.none(),
                null,
                LocalDate.parse("2026-05-01"),
                null
        ));
        Task generalTask = taskRepository.save(Task.create(user, studyCategory, "지난 일반", LocalDate.parse("2026-05-25"), null));
        notificationSettingRepository.save(NotificationSetting.create(repeatTask, 1440, true));
        notificationSettingRepository.save(NotificationSetting.create(generalTask, 1440, true));

        org.assertj.core.api.Assertions.assertThat(repeatDeadlineRefreshService.generateDueOccurrences()).isEqualTo(1);
        Task occurrence = taskRepository.findBySourceTaskIdAndDeadline(repeatTask.getId(), LocalDate.parse("2026-06-01"))
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(occurrence.getSourceTask().getId()).isEqualTo(repeatTask.getId());
        org.assertj.core.api.Assertions.assertThat(occurrence.getStatus()).isEqualTo(TaskStatus.TODO);
        org.assertj.core.api.Assertions.assertThat(occurrence.getEstimatedTime()).isEqualTo(repeatTask.getEstimatedTime());
        org.assertj.core.api.Assertions.assertThat(repeatRuleRepository.existsByTaskId(occurrence.getId())).isFalse();
        org.assertj.core.api.Assertions.assertThat(notificationSettingRepository.findByTaskId(occurrence.getId()))
                .isPresent()
                .get()
                .extracting(NotificationSetting::getNotifyBefore)
                .isEqualTo(1440);
        org.assertj.core.api.Assertions.assertThat(taskRepository.findById(repeatTask.getId()).orElseThrow().getDeadline())
                .isEqualTo(LocalDate.parse("2026-05-25"));
        org.assertj.core.api.Assertions.assertThat(taskRepository.findById(generalTask.getId()).orElseThrow().getDeadline())
                .isEqualTo(LocalDate.parse("2026-05-25"));

        org.assertj.core.api.Assertions.assertThat(repeatDeadlineRefreshService.generateDueOccurrences()).isZero();
    }

    @Test
    void repeatedTaskGenerationDoesNotCreateDuplicateOccurrenceWhenExistingOccurrenceExists() {
        Task repeatTask = taskRepository.save(Task.create(user, studyCategory, "지난 반복", LocalDate.parse("2026-05-25"), null));
        repeatRuleRepository.save(RepeatRule.create(
                repeatTask,
                RecurrenceType.WEEKLY,
                List.of(1),
                RepeatMonthDays.none(),
                null,
                LocalDate.parse("2026-05-01"),
                null
        ));
        taskRepository.save(Task.createRepeatedOccurrence(repeatTask, LocalDate.parse("2026-06-01")));

        org.assertj.core.api.Assertions.assertThat(repeatDeadlineRefreshService.generateDueOccurrences()).isZero();
    }

    @Test
    void historyReturnsDoneTasksWithTaskSessionElapsedTime() throws Exception {
        Task done = saveTask(user, studyCategory, "이전 완료", LocalDate.parse("2026-08-01"), null, TaskStatus.DONE);
        saveTask(user, studyCategory, "진행 중", LocalDate.parse("2026-08-02"), null, TaskStatus.IN_PROGRESS);
        saveTask(otherUser, categoryRepository.save(Category.create(otherUser, "타인", "#B55CFF", 1, false)), "타인 완료", LocalDate.parse("2026-08-03"), null, TaskStatus.DONE);
        taskSessionRepository.save(TaskSession.create(done, user, LocalDateTime.now(), LocalDateTime.now(), 1200, TaskSessionStatus.DONE));
        taskSessionRepository.save(TaskSession.create(done, user, LocalDateTime.now(), LocalDateTime.now(), 1800, TaskSessionStatus.DONE));

        mockMvc.perform(get("/api/tasks/history")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "이전")
                        .param("categoryId", String.valueOf(studyCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].taskId").value(done.getId()))
                .andExpect(jsonPath("$.data.content[0].deadline").value("2026-08-01"))
                .andExpect(jsonPath("$.data.content[0].totalElapsedTime").value(3000));
    }

    @Test
    void historySearchEscapesLikeWildcardsAndRejectsTooLongKeyword() throws Exception {
        saveTask(user, studyCategory, "100% 완료", LocalDate.parse("2026-08-01"), null, TaskStatus.DONE);
        saveTask(user, studyCategory, "일반 완료", LocalDate.parse("2026-08-02"), null, TaskStatus.DONE);

        mockMvc.perform(get("/api/tasks/history")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("100% 완료"));

        mockMvc.perform(get("/api/tasks/history")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void similarTaskRulesUpdateAndDeleteWork() throws Exception {
        Task done = saveTask(user, studyCategory, "이전 완료", LocalDate.parse("2026-08-01"), null, TaskStatus.DONE);
        Task secondDone = saveTask(user, studyCategory, "두번째 완료", LocalDate.parse("2026-08-03"), null, TaskStatus.DONE);
        Task incomplete = saveTask(user, studyCategory, "진행 중", LocalDate.parse("2026-08-02"), null, TaskStatus.IN_PROGRESS);
        taskSessionRepository.save(TaskSession.create(done, user, LocalDateTime.now(), LocalDateTime.now(), 1200, TaskSessionStatus.DONE));
        taskSessionRepository.save(TaskSession.create(done, user, LocalDateTime.now(), LocalDateTime.now(), 1800, TaskSessionStatus.DONE));
        taskSessionRepository.save(TaskSession.create(secondDone, user, LocalDateTime.now(), LocalDateTime.now(), 3600, TaskSessionStatus.DONE));

        String createResponse = mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "유사 연결",
                                  "deadline": "2026-08-12",
                                  "categoryId": %d,
                                  "similarTaskId": %d
                                }
                                """.formatted(studyCategory.getId(), done.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.similarTaskId").value(done.getId()))
                .andExpect(jsonPath("$.data.estimatedTime").value(50))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.taskId")).longValue();

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "미완료 연결",
                                  "deadline": "2026-08-12",
                                  "categoryId": %d,
                                  "similarTaskId": %d
                                }
                                """.formatted(studyCategory.getId(), incomplete.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIMILAR_TASK_NOT_DONE"));

        mockMvc.perform(patch("/api/tasks/{taskId}", taskId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notifyBefore": 10080,
                                  "notificationEnabled": false,
                                  "starred": true,
                                  "clearSimilarTask": true,
                                  "clearRepeatRule": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationSetting.notifyBefore").value(10080))
                .andExpect(jsonPath("$.data.notificationSetting.enabled").value(false))
                .andExpect(jsonPath("$.data.starred").value(true))
                .andExpect(jsonPath("$.data.similarTaskId").doesNotExist());

        mockMvc.perform(patch("/api/tasks/{taskId}", taskId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "similarTaskId": %d
                                }
                                """.formatted(secondDone.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.similarTaskId").value(secondDone.getId()))
                .andExpect(jsonPath("$.data.estimatedTime").value(60));

        mockMvc.perform(patch("/api/tasks/{taskId}", taskId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "similarTaskId": %d
                                }
                                """.formatted(taskId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_SIMILAR_TASK_NOT_ALLOWED"));

        mockMvc.perform(delete("/api/tasks/{taskId}", done.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void deletingReferencedSimilarTaskClearsRemainingTaskLink() throws Exception {
        Task deleteTarget = saveTask(user, studyCategory, "삭제 대상", LocalDate.parse("2026-08-01"), null, TaskStatus.DONE);
        Task remaining = saveTask(user, studyCategory, "남는 과업", LocalDate.parse("2026-08-12"), deleteTarget, TaskStatus.TODO);

        mockMvc.perform(delete("/api/tasks/{taskId}", deleteTarget.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(taskRepository.findById(remaining.getId()))
                .isPresent()
                .get()
                .extracting(Task::getSimilarTask)
                .isNull();
    }

    @Test
    void repeatedTaskDeadlineOnlyUpdateIsRejectedUnlessRepeatRuleIsExplicitlyCleared() throws Exception {
        Task repeatTask = saveTask(user, studyCategory, "반복 과업", LocalDate.parse("2026-06-03"), null, TaskStatus.TODO);
        repeatRuleRepository.save(RepeatRule.create(
                repeatTask,
                RecurrenceType.WEEKLY,
                List.of(3),
                RepeatMonthDays.none(),
                null,
                LocalDate.parse("2026-06-03"),
                null
        ));

        mockMvc.perform(patch("/api/tasks/{taskId}", repeatTask.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deadline": "2026-08-12"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECURRENCE"));

        mockMvc.perform(patch("/api/tasks/{taskId}", repeatTask.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deadline": "2026-08-12",
                                  "clearRepeatRule": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deadline").value("2026-08-12"))
                .andExpect(jsonPath("$.data.repeatRule").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(repeatRuleRepository.existsByTaskId(repeatTask.getId())).isFalse();
    }

    @Test
    void otherUserTaskAccessFails() throws Exception {
        Task otherTask = saveTask(otherUser, categoryRepository.save(Category.create(otherUser, "타인2", "#FFB85C", 2, false)), "타인 과업", LocalDate.parse("2026-08-12"), null, TaskStatus.TODO);

        mockMvc.perform(get("/api/tasks/{taskId}", otherTask.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

        mockMvc.perform(patch("/api/tasks/{taskId}", otherTask.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정되면 안 됨",
                                  "deadline": "2026-08-20"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

        mockMvc.perform(delete("/api/tasks/{taskId}", otherTask.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

        Task unchanged = taskRepository.findById(otherTask.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unchanged.getTitle()).isEqualTo("타인 과업");
        org.assertj.core.api.Assertions.assertThat(unchanged.getDeadline()).isEqualTo(LocalDate.parse("2026-08-12"));
    }

    private Task saveTask(User owner, Category category, String title, LocalDate deadline, Task similarTask, TaskStatus status) {
        Task task = taskRepository.save(Task.create(owner, category, title, deadline, similarTask));
        task.updateStatus(status);
        notificationSettingRepository.save(NotificationSetting.create(task, 1440, true));
        return task;
    }
}

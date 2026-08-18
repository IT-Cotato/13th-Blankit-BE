package com.cotato.blankit.domain.task;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.task.entity.NotificationSetting;
import com.cotato.blankit.domain.task.entity.RecurrenceType;
import com.cotato.blankit.domain.task.entity.RepeatMonthDays;
import com.cotato.blankit.domain.task.entity.RepeatRule;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.task.repository.NotificationSettingRepository;
import com.cotato.blankit.domain.task.repository.RepeatRuleRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.notification.push.repository.PushNotificationJobRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.task.service.RepeatDeadlineRefreshService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.support.AccessTokenTestFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    private UserNotificationSettingRepository userNotificationSettingRepository;

    @Autowired
    private PushNotificationJobRepository pushNotificationJobRepository;

    @Autowired
    private RepeatRuleRepository repeatRuleRepository;

    @Autowired
    private TaskSessionRepository taskSessionRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private RepeatDeadlineRefreshService repeatDeadlineRefreshService;

    @Autowired
    private AccessTokenTestFactory accessTokenTestFactory;

    @PersistenceContext
    private EntityManager entityManager;

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
        studyCategory = categoryRepository.save(Category.create(user, "학업", "#FC5F5F", "book", 0, true));
        workCategory = categoryRepository.save(Category.create(user, "업무", "#FF9A33", "briefcase", 1, false));
        categoryRepository.save(Category.create(otherUser, "학업", "#FC5F5F", "book", 0, true));
        token = accessTokenTestFactory.createAccessToken(user.getId());
    }

    @Test
    void formOptionsReturnsErdDefaults() throws Exception {
        mockMvc.perform(get("/api/tasks/form-options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultCategoryId").value(studyCategory.getId()))
                .andExpect(jsonPath("$.data.defaultReminderOffsetMinutes").value(1440))
                .andExpect(jsonPath("$.data.defaultRepeatEnabled").value(false))
                .andExpect(jsonPath("$.data.categories[0].color").value("#FC5F5F"))
                .andExpect(jsonPath("$.data.reminderRange.minimumMinutes").value(1440))
                .andExpect(jsonPath("$.data.reminderOptions.length()").value(3))
                .andExpect(jsonPath("$.data.reminderOptions[0]").value(1440))
                .andExpect(jsonPath("$.data.reminderOptions[1]").value(4320))
                .andExpect(jsonPath("$.data.reminderOptions[2]").value(10080));
    }

    @Test
    void creatingTaskSchedulesDeadlinePushWhenBothNotificationSettingsAreEnabled() throws Exception {
        UserNotificationSetting userSetting = UserNotificationSetting.createDefault(user);
        userSetting.update(true, false);
        userNotificationSettingRepository.saveAndFlush(userSetting);

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "마감 알림 과업",
                                  "categoryId": %d,
                                  "deadline": "2026-06-05",
                                  "notifyBefore": 1440,
                                  "notificationEnabled": true
                                }
                                """.formatted(studyCategory.getId())))
                .andExpect(status().isCreated());

        assertThat(com.cotato.blankit.domain.notification.push.PushJobTestQueries.findByUserAndType(
                pushNotificationJobRepository, user.getId(), PushNotificationType.TASK_DEADLINE))
                .singleElement()
                .satisfies(job -> assertThat(job.getScheduledAt())
                        .isEqualTo(LocalDateTime.of(2026, 6, 4, 9, 0)));
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
                                  "color": "#FBF965",
                                  "iconKey": "book-open"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.categoryName").value("학업"))
                .andExpect(jsonPath("$.data.color").value("#FBF965"))
                .andExpect(jsonPath("$.data.iconKey").value("book-open"));

        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "색상 중복",
                                  "color": "#FBF965",
                                  "iconKey": "duplicate"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_COLOR_ALREADY_USED"));

        String otherToken = accessTokenTestFactory.createAccessToken(otherUser.getId());
        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "다른 사용자",
                                  "color": "#FBF965",
                                  "iconKey": "user"
                                }
                                """))
                .andExpect(status().isCreated());

        Category customColorCategory = categoryRepository.save(Category.create(user, "커스텀", "#D3FB65", "star", 3, false));
        mockMvc.perform(patch("/api/categories/{categoryId}", customColorCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "커스텀 수정",
                                  "color": "#5BE478",
                                  "iconKey": "heart"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("커스텀 수정"))
                .andExpect(jsonPath("$.data.color").value("#5BE478"))
                .andExpect(jsonPath("$.data.iconKey").value("heart"));

        org.assertj.core.api.Assertions.assertThat(categoryRepository.findById(customColorCategory.getId()))
                .isPresent()
                .get()
                .satisfies(category -> {
                    org.assertj.core.api.Assertions.assertThat(category.getName()).isEqualTo("커스텀 수정");
                    org.assertj.core.api.Assertions.assertThat(category.getColor()).isEqualTo("#5BE478");
                    org.assertj.core.api.Assertions.assertThat(category.getIconKey()).isEqualTo("heart");
                });

        mockMvc.perform(patch("/api/categories/{categoryId}", customColorCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "color": "#FC5F5F"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_COLOR_ALREADY_USED"));

        Category otherOwnedCategory = categoryRepository.save(Category.create(otherUser, "타인수정", "#D3FB65", "user", 5, false));
        mockMvc.perform(patch("/api/categories/{categoryId}", otherOwnedCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "수정되면 안 됨",
                                  "color": "#5BE4CB"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        org.assertj.core.api.Assertions.assertThat(categoryRepository.findById(otherOwnedCategory.getId()))
                .isPresent()
                .get()
                .extracting(Category::getName)
                .isEqualTo("타인수정");

        mockMvc.perform(get("/api/categories/available-colors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data", hasItem("#D3FB65")));

        mockMvc.perform(delete("/api/categories/{categoryId}", workCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/categories/available-colors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasItem("#FF9A33")));

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.categoryId == %d)]".formatted(workCategory.getId())).doesNotExist());
    }

    @Test
    void createCategoryRejectsMissingOrBlankIconKey() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "아이콘 누락",
                                  "color": "#D3FB65"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "아이콘 공백",
                                  "color": "#5BE478",
                                  "iconKey": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoryCreateRejectsColorOutsideFixedPalette() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "임의 색상",
                                  "color": "#123456",
                                  "iconKey": "star"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void categoryUpdateRejectsColorOutsideFixedPalette() throws Exception {
        mockMvc.perform(patch("/api/categories/{categoryId}", studyCategory.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "color": "#123456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void availableColorsExcludesAllThreeDefaultCategoryColors() throws Exception {
        categoryRepository.save(Category.create(user, "기념일", "#FBF965", "pin", 2, true));

        mockMvc.perform(get("/api/categories/available-colors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[?(@ == '#FC5F5F')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@ == '#FF9A33')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@ == '#FBF965')]").doesNotExist());
    }

    @Test
    void availableColorsReturnsEmptyWhenAllFixedColorsAreUsed() throws Exception {
        List<String> remainingColors = List.of(
                "#FBF965", "#D3FB65", "#5BE478", "#5BE4CB",
                "#6FD4FF", "#B3BBFA", "#F2B3FA", "#C5C9CD"
        );
        for (int index = 0; index < remainingColors.size(); index++) {
            categoryRepository.save(Category.create(
                    user,
                    "카테고리 " + index,
                    remainingColors.get(index),
                    "star",
                    index + 2,
                    false
            ));
        }

        mockMvc.perform(get("/api/categories/available-colors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void updateCategoryWithoutIconKeyKeepsExistingValue() throws Exception {
        Category category = categoryRepository.save(
                Category.create(user, "부분 수정", "#D3FB65", "star", 3, false)
        );

        mockMvc.perform(patch("/api/categories/{categoryId}", category.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "부분 수정 완료"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("부분 수정 완료"))
                .andExpect(jsonPath("$.data.iconKey").value("star"));

        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(category.getId()))
                .isPresent()
                .get()
                .extracting(Category::getIconKey)
                .isEqualTo("star");
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
        String freshToken = accessTokenTestFactory.createAccessToken(freshUser.getId());

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
        Category deletedCategory = categoryRepository.save(Category.create(user, "삭제", "#B55CFF", "trash", 2, false));
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

        Category otherCategory = categoryRepository.save(Category.create(otherUser, "타인", "#FBF965", "user", 1, false));
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
                                  "title": "제거된 10분 알림",
                                  "deadline": "2026-08-12",
                                  "notifyBefore": 10
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
                                  "title": "제거된 1시간 알림",
                                  "deadline": "2026-08-12",
                                  "notifyBefore": 60
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

        org.assertj.core.api.Assertions.assertThat(repeatDeadlineRefreshService.generateDueOccurrences()).isEqualTo(2);
        Task occurrence = taskRepository.findBySourceTaskIdAndDeadline(repeatTask.getId(), LocalDate.parse("2026-06-01"))
                .orElseThrow();
        Task nextOccurrence = taskRepository.findBySourceTaskIdAndDeadline(
                        repeatTask.getId(),
                        LocalDate.parse("2026-06-08")
                )
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
        org.assertj.core.api.Assertions.assertThat(nextOccurrence.getSourceTask().getId())
                .isEqualTo(repeatTask.getId());
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

        org.assertj.core.api.Assertions.assertThat(repeatDeadlineRefreshService.generateDueOccurrences()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(taskRepository.findBySourceTaskIdAndDeadline(
                        repeatTask.getId(),
                        LocalDate.parse("2026-06-08")
                ))
                .isPresent();
        org.assertj.core.api.Assertions.assertThat(repeatDeadlineRefreshService.generateDueOccurrences()).isZero();
    }

    @Test
    void repeatedTaskGenerationRecoversMissedOccurrencesAndKeepsOneFutureOccurrence() {
        Task repeatTask = taskRepository.save(Task.create(
                user,
                studyCategory,
                "중단 복구 반복",
                LocalDate.parse("2026-05-11"),
                null
        ));
        repeatRuleRepository.save(RepeatRule.create(
                repeatTask,
                RecurrenceType.WEEKLY,
                List.of(1),
                RepeatMonthDays.none(),
                null,
                LocalDate.parse("2026-05-01"),
                null
        ));

        org.assertj.core.api.Assertions.assertThat(
                repeatDeadlineRefreshService.generateDueOccurrences()
        ).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(
                taskRepository.findTopBySourceTaskIdOrderByDeadlineDescIdDesc(repeatTask.getId())
        )
                .isPresent()
                .get()
                .extracting(Task::getDeadline)
                .isEqualTo(LocalDate.parse("2026-06-08"));
        org.assertj.core.api.Assertions.assertThat(
                repeatDeadlineRefreshService.generateDueOccurrences()
        ).isZero();
    }

    @Test
    void sourceNotificationSettingChangePropagatesToFutureOccurrence() throws Exception {
        Task repeatTask = taskRepository.save(Task.create(
                user,
                studyCategory,
                "알림 동기화 반복",
                LocalDate.parse("2026-06-01"),
                null
        ));
        repeatRuleRepository.save(RepeatRule.create(
                repeatTask,
                RecurrenceType.WEEKLY,
                List.of(1),
                RepeatMonthDays.none(),
                null,
                LocalDate.parse("2026-06-01"),
                null
        ));
        notificationSettingRepository.save(NotificationSetting.create(repeatTask, 1440, true));
        repeatDeadlineRefreshService.generateDueOccurrences();
        Task futureOccurrence = taskRepository.findBySourceTaskIdAndDeadline(
                repeatTask.getId(),
                LocalDate.parse("2026-06-08")
        ).orElseThrow();

        mockMvc.perform(patch("/api/tasks/{taskId}", repeatTask.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notifyBefore": 4320,
                                  "notificationEnabled": false
                                }
                                """))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                notificationSettingRepository.findByTaskId(futureOccurrence.getId())
        )
                .isPresent()
                .get()
                .satisfies(setting -> {
                    org.assertj.core.api.Assertions.assertThat(setting.getNotifyBefore()).isEqualTo(4320);
                    org.assertj.core.api.Assertions.assertThat(setting.isEnabled()).isFalse();
                });
    }

    @Test
    void repeatRuleChangeDeletesOldFutureOccurrenceAndRecalculatesSourceDeadline() throws Exception {
        Task repeatTask = taskRepository.save(Task.create(
                user,
                studyCategory,
                "규칙 변경 반복",
                LocalDate.parse("2026-06-01"),
                null
        ));
        repeatRuleRepository.save(RepeatRule.create(
                repeatTask,
                RecurrenceType.WEEKLY,
                List.of(1),
                RepeatMonthDays.none(),
                null,
                LocalDate.parse("2026-06-01"),
                null
        ));
        notificationSettingRepository.save(NotificationSetting.create(repeatTask, 1440, true));
        repeatDeadlineRefreshService.generateDueOccurrences();

        mockMvc.perform(patch("/api/tasks/{taskId}", repeatTask.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repeatRule": {
                                    "frequency": "WEEKLY",
                                    "daysOfWeek": [2],
                                    "startDate": "2026-06-01"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deadline").value("2026-06-02"));

        org.assertj.core.api.Assertions.assertThat(taskRepository.findBySourceTaskIdAndDeadline(
                repeatTask.getId(),
                LocalDate.parse("2026-06-08")
        )).isEmpty();
    }

    @Test
    void historyReturnsDoneTasksWithTaskSessionElapsedTime() throws Exception {
        Task done = saveTask(user, studyCategory, "이전 완료", LocalDate.parse("2026-08-01"), null, TaskStatus.DONE);
        saveTask(user, studyCategory, "진행 중", LocalDate.parse("2026-08-02"), null, TaskStatus.IN_PROGRESS);
        saveTask(otherUser, categoryRepository.save(Category.create(otherUser, "타인", "#B55CFF", "user", 1, false)), "타인 완료", LocalDate.parse("2026-08-03"), null, TaskStatus.DONE);
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
        Task futureOccurrence = taskRepository.save(Task.createRepeatedOccurrence(
                repeatTask,
                LocalDate.parse("2026-06-10")
        ));
        notificationSettingRepository.save(NotificationSetting.create(futureOccurrence, 1440, true));

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
        org.assertj.core.api.Assertions.assertThat(taskRepository.findById(futureOccurrence.getId())).isEmpty();
    }

    @Test
    void otherUserTaskAccessFails() throws Exception {
        Task otherTask = saveTask(otherUser, categoryRepository.save(Category.create(otherUser, "타인2", "#FBF965", "user", 2, false)), "타인 과업", LocalDate.parse("2026-08-12"), null, TaskStatus.TODO);

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

    @Test
    void deleteTask_withFeedback_succeeds() throws Exception {
        // 피드백이 있는 과업 삭제 시 과업·세션·피드백이 모두 삭제됨
        Task task = saveTask(user, studyCategory, "피드백 있는 과업", LocalDate.parse("2026-08-01"), null, TaskStatus.TODO);
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(task, user, LocalDateTime.now(), null, 600, TaskSessionStatus.PAUSED));
        Feedback feedback = feedbackRepository.save(Feedback.create(session, task, user, 50, "메모", true));

        mockMvc.perform(delete("/api/tasks/{taskId}", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(taskRepository.findById(task.getId())).isEmpty();
        assertThat(taskSessionRepository.findById(session.getTaskSessionId())).isEmpty();
        assertThat(feedbackRepository.findById(feedback.getFeedbackId())).isEmpty();
    }

    @Test
    void updateStarred_activatesStarAndPersists() throws Exception {
        Task task = saveTask(user, studyCategory, "별표 설정 대상", LocalDate.parse("2026-08-12"), null, TaskStatus.TODO);

        mockMvc.perform(patch("/api/tasks/{taskId}/star", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "isStarred": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.starred").value(true));

        // 멱등성: 이미 starred=true인 상태에서 true 재요청 → 200 + starred=true
        mockMvc.perform(patch("/api/tasks/{taskId}/star", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "isStarred": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.starred").value(true));

        entityManager.flush();
        entityManager.clear();
        assertThat(taskRepository.findById(task.getId()).orElseThrow().isStarred()).isTrue();
    }

    @Test
    void updateStarred_deactivatesStarAndPersists() throws Exception {
        Task task = saveTask(user, studyCategory, "별표 해제 대상", LocalDate.parse("2026-08-12"), null, TaskStatus.TODO);
        task.updateStarred(true);

        mockMvc.perform(patch("/api/tasks/{taskId}/star", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "isStarred": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.starred").value(false));

        // 멱등성: 이미 starred=false인 상태에서 false 재요청 → 200 + starred=false
        mockMvc.perform(patch("/api/tasks/{taskId}/star", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "isStarred": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.starred").value(false));

        entityManager.flush();
        entityManager.clear();
        assertThat(taskRepository.findById(task.getId()).orElseThrow().isStarred()).isFalse();
    }

    @Test
    void updateStarred_otherUserTaskReturnsNotFoundAndStarUnchanged() throws Exception {
        Category otherCategory = categoryRepository.save(Category.create(otherUser, "타인별표", "#B55CFF", "user", 3, false));
        Task otherTask = saveTask(otherUser, otherCategory, "타인 과업", LocalDate.parse("2026-08-12"), null, TaskStatus.TODO);

        mockMvc.perform(patch("/api/tasks/{taskId}/star", otherTask.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "isStarred": true }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

        entityManager.flush();
        entityManager.clear();
        org.assertj.core.api.Assertions.assertThat(taskRepository.findById(otherTask.getId()).orElseThrow().isStarred()).isFalse();
    }

    @Test
    void updateStarred_missingIsStarredReturnsBadRequest() throws Exception {
        Task task = saveTask(user, studyCategory, "필드 누락 과업", LocalDate.parse("2026-08-12"), null, TaskStatus.TODO);

        mockMvc.perform(patch("/api/tasks/{taskId}/star", task.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data[0].field").value("isStarred"))
                .andExpect(jsonPath("$.data[0].message").value("isStarred 값은 필수입니다."));
    }

    @Test
    void getTaskDetail_progressRateAppearsInResponse() throws Exception {
        Task withProgress = saveTask(user, studyCategory, "진행률 과업", LocalDate.of(2026, 8, 12), null, TaskStatus.TODO);
        withProgress.updateProgressRate(45);
        Task noProgress = saveTask(user, studyCategory, "진행률 없는 과업", LocalDate.of(2026, 8, 13), null, TaskStatus.TODO);

        mockMvc.perform(get("/api/tasks/{taskId}", withProgress.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressRate").value(45));

        mockMvc.perform(get("/api/tasks/{taskId}", noProgress.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressRate").value((Object) null));
    }

    @Test
    void getTaskList_progressRateAppearsInResponse() throws Exception {
        Task withProgress = saveTask(user, studyCategory, "진행률 과업", LocalDate.of(2026, 8, 12), null, TaskStatus.TODO);
        withProgress.updateProgressRate(60);
        saveTask(user, studyCategory, "진행률 없는 과업", LocalDate.of(2026, 8, 13), null, TaskStatus.TODO);

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].progressRate").value(60));

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-08-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].progressRate").value((Object) null));
    }

    @Test
    void getTaskList_memoAppearsInResponse() throws Exception {
        // 최종 제출 피드백이 있는 과업: memo 포함
        Task taskWithMemo = saveTask(user, studyCategory, "메모 있는 과업", LocalDate.of(2026, 8, 12), null, TaskStatus.TODO);
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskWithMemo, user, LocalDateTime.now(), LocalDateTime.now(), 600, TaskSessionStatus.DONE));
        feedbackRepository.save(Feedback.create(session, taskWithMemo, user, 50, "피드백 메모 내용", false));

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].memo").value("피드백 메모 내용"));

        // 임시저장(isDraft=true)만 있는 과업: memo는 null
        Task taskDraftOnly = saveTask(user, studyCategory, "임시저장만 있는 과업", LocalDate.of(2026, 8, 13), null, TaskStatus.TODO);
        TaskSession draftSession = taskSessionRepository.save(
                TaskSession.create(taskDraftOnly, user, LocalDateTime.now(), null, 0, TaskSessionStatus.PLAYING));
        feedbackRepository.save(Feedback.create(draftSession, taskDraftOnly, user, 30, "임시 메모", true));

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-08-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].memo").value((Object) null));

        // 피드백 없는 과업: memo는 null
        saveTask(user, studyCategory, "피드백 없는 과업", LocalDate.of(2026, 8, 14), null, TaskStatus.TODO);

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-08-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].memo").value((Object) null));

        // 최종 제출이지만 메모가 blank: null 반환
        Task taskBlankMemo = saveTask(user, studyCategory, "빈 메모 과업", LocalDate.of(2026, 8, 15), null, TaskStatus.TODO);
        TaskSession blankSession = taskSessionRepository.save(
                TaskSession.create(taskBlankMemo, user, LocalDateTime.now(), LocalDateTime.now(), 300, TaskSessionStatus.DONE));
        feedbackRepository.save(Feedback.create(blankSession, taskBlankMemo, user, 20, "  ", false));

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-08-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].memo").value((Object) null));
    }

    private Task saveTask(User owner, Category category, String title, LocalDate deadline, Task similarTask, TaskStatus status) {
        Task task = taskRepository.save(Task.create(owner, category, title, deadline, similarTask));
        task.updateStatus(status);
        notificationSettingRepository.save(NotificationSetting.create(task, 1440, true));
        return task;
    }
}

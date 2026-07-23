package com.cotato.blankit.domain.search;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.search.repository.SearchHistoryRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:search-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class SearchControllerTest {

    private MockMvc mockMvc;
    private User user;
    private User otherUser;
    private Category studyCategory;
    private String token;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SearchHistoryRepository searchHistoryRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        searchHistoryRepository.deleteAll();
        taskRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.create(SocialProvider.KAKAO, "search-user", "search@example.com", "서치", null, 120));
        otherUser = userRepository.save(User.create(SocialProvider.KAKAO, "other-search-user", "other-search@example.com", "다른사용자", null, 120));
        studyCategory = categoryRepository.save(Category.create(user, "학업", "#5C9EFF", 0, true));
        token = jwtTokenProvider.createAccessToken(user.getId());
    }

    @Test
    void searchApiReturnsTasksByKeyword() throws Exception {
        Task matchingTask = saveTask(user, studyCategory, "수학 기말고사 준비", LocalDate.parse("2026-07-20"), TaskStatus.IN_PROGRESS);
        saveTask(user, studyCategory, "영어 단어 암기", LocalDate.parse("2026-07-21"), TaskStatus.TODO);
        Task deletedTask = saveTask(user, studyCategory, "수학 삭제 과업", LocalDate.parse("2026-07-23"), TaskStatus.TODO);
        taskRepository.delete(deletedTask);
        taskRepository.flush();
        Category otherCategory = categoryRepository.save(Category.create(otherUser, "타인", "#B55CFF", 1, false));
        saveTask(otherUser, otherCategory, "수학 타인 과업", LocalDate.parse("2026-07-22"), TaskStatus.TODO);

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", " 수학 ")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.tasks[0].taskId").value(matchingTask.getId()))
                .andExpect(jsonPath("$.data.tasks[0].title").value("수학 기말고사 준비"))
                .andExpect(jsonPath("$.data.tasks[0].categoryId").value(studyCategory.getId()))
                .andExpect(jsonPath("$.data.tasks[0].categoryName").value("학업"))
                .andExpect(jsonPath("$.data.tasks[0].categoryColor").value("#5C9EFF"))
                .andExpect(jsonPath("$.data.tasks[0].deadline").value("2026-07-20"))
                .andExpect(jsonPath("$.data.tasks[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.tasks[0].progressRate").value(0));
    }

    @Test
    void searchApiRejectsMissingBlankAndTooLongKeywordWithoutSavingHistory() throws Exception {
        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        org.assertj.core.api.Assertions.assertThat(searchHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void searchApiReturnsEmptyListAndSavesValidKeyword() throws Exception {
        mockMvc.perform(get("/api/search")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "없는과업"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.tasks.length()").value(0));

        org.assertj.core.api.Assertions.assertThat(searchHistoryRepository.findByUserIdAndKeyword(user.getId(), "없는과업"))
                .isPresent();
    }

    @Test
    void searchApiRequiresAuthenticationAndIsIncludedInSwaggerDocs() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("keyword", "수학"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/v3/api-docs/1. 구현 완료"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/search']").exists())
                .andExpect(jsonPath("$.paths['/api/search'].get.parameters[?(@.name == 'keyword')]").exists())
                .andExpect(jsonPath("$.paths['/api/search'].get.parameters[?(@.name == 'page')]").exists())
                .andExpect(jsonPath("$.paths['/api/search'].get.parameters[?(@.name == 'size')]").exists());
    }

    private Task saveTask(User owner, Category category, String title, LocalDate deadline, TaskStatus status) {
        Task task = taskRepository.save(Task.create(owner, category, title, deadline, null));
        task.updateStatus(status);
        return taskRepository.save(task);
    }
}

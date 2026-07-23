package com.cotato.blankit.domain.feedback;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.task.entity.Task;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:feedback-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class FeedbackControllerTest {

    private MockMvc mockMvc;
    private User user;
    private String token;
    private Task taskA;
    private Task taskB;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskSessionRepository taskSessionRepository;

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

        user = userRepository.save(User.create(SocialProvider.KAKAO, "feedback-user", "feedback@example.com", "테스트유저", null, 120));
        token = jwtTokenProvider.createAccessToken(user.getId());

        Category category = categoryRepository.save(Category.create(user, "학업", "#5C9EFF", 0, true));
        taskA = taskRepository.save(Task.create(user, category, "과업A", LocalDate.of(2026, 7, 31), null));
        taskB = taskRepository.save(Task.create(user, category, "과업B", LocalDate.of(2026, 7, 31), null));
    }

    @Test
    void 다른_과업_PLAYING_중_PLAYING_요청시_409_반환() throws Exception {
        // 과업A 세션이 PLAYING 중
        TaskSession playingSession = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 300, TaskSessionStatus.PLAYING));

        // 과업B 세션 생성 후 PLAYING 요청
        TaskSession sessionB = taskSessionRepository.save(
                TaskSession.create(taskB, user, LocalDateTime.now(), null, 0, TaskSessionStatus.PAUSED));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", sessionB.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PLAYING",
                                  "elapsedTime": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_PLAYING"));
    }

    @Test
    void PLAYING_세션_없을때_PLAYING_요청시_정상_처리() throws Exception {
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 0, TaskSessionStatus.PAUSED));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PLAYING",
                                  "elapsedTime": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLAYING"));
    }

    @Test
    void PLAYING_중인_세션을_PAUSED로_변경하면_정상_처리() throws Exception {
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 300, TaskSessionStatus.PLAYING));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PAUSED",
                                  "elapsedTime": 600
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.elapsedTime").value(600));
    }
}

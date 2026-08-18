package com.cotato.blankit.domain.task;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.support.AccessTokenTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-stats-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class TaskStatsControllerTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AccessTokenTestFactory accessTokenTestFactory;

    private MockMvc mockMvc;
    private String token;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        User user = userRepository.save(User.create(SocialProvider.KAKAO, "stats-ctrl-user", "stats-ctrl@example.com", "통계유저", null, 120));
        categoryRepository.save(Category.create(user, "학업", "#FF5C5C", "book", 0, true));
        token = accessTokenTestFactory.createAccessToken(user.getId());
    }

    // ── GET /api/v1/tasks/stats/monthly ──────────────────────────────────────

    @Nested
    @DisplayName("월별 통계 year/month 범위 검증")
    class GetMonthlyStats {

        @ParameterizedTest(name = "year={0}")
        @ValueSource(ints = {1969, 0, -1, 10000})
        @DisplayName("범위 밖 year는 400을 반환한다")
        void invalidYear_returns400(int year) throws Exception {
            mockMvc.perform(get("/api/v1/tasks/stats/monthly")
                            .header("Authorization", "Bearer " + token)
                            .param("year", String.valueOf(year))
                            .param("month", "7"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @ParameterizedTest(name = "month={0}")
        @ValueSource(ints = {0, 13, -1, 100})
        @DisplayName("범위 밖 month는 400을 반환한다")
        void invalidMonth_returns400(int month) throws Exception {
            mockMvc.perform(get("/api/v1/tasks/stats/monthly")
                            .header("Authorization", "Bearer " + token)
                            .param("year", "2026")
                            .param("month", String.valueOf(month)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("유효한 year/month는 200을 반환한다")
        void validYearMonth_returns200() throws Exception {
            mockMvc.perform(get("/api/v1/tasks/stats/monthly")
                            .header("Authorization", "Bearer " + token)
                            .param("year", "2026")
                            .param("month", "8"))
                    .andExpect(status().isOk());
        }
    }

    // ── GET /api/tasks/calendar ───────────────────────────────────────────────

    @Nested
    @DisplayName("캘린더 과업 조회 year/month 범위 검증")
    class GetMonthlyCalendar {

        @ParameterizedTest(name = "year={0}")
        @ValueSource(ints = {1969, 0, -1, 10000})
        @DisplayName("범위 밖 year는 400을 반환한다")
        void invalidYear_returns400(int year) throws Exception {
            mockMvc.perform(get("/api/tasks/calendar")
                            .header("Authorization", "Bearer " + token)
                            .param("year", String.valueOf(year))
                            .param("month", "7"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @ParameterizedTest(name = "month={0}")
        @ValueSource(ints = {0, 13, -1, 100})
        @DisplayName("범위 밖 month는 400을 반환한다")
        void invalidMonth_returns400(int month) throws Exception {
            mockMvc.perform(get("/api/tasks/calendar")
                            .header("Authorization", "Bearer " + token)
                            .param("year", "2026")
                            .param("month", String.valueOf(month)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("유효한 year/month는 200을 반환한다")
        void validYearMonth_returns200() throws Exception {
            mockMvc.perform(get("/api/tasks/calendar")
                            .header("Authorization", "Bearer " + token)
                            .param("year", "2026")
                            .param("month", "8"))
                    .andExpect(status().isOk());
        }
    }
}

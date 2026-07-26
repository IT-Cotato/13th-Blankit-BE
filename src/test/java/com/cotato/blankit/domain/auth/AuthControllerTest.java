package com.cotato.blankit.domain.auth;

import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class AuthControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserNotificationSettingRepository userNotificationSettingRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void socialSignupSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "signup-1",
                                  "socialToken": "verified:KAKAO:signup-1",
                                  "email": "user@example.com",
                                  "nickname": "서윤",
                                  "profileImageUrl": "https://example.com/profile.png",
                                  "recommendedDailyTime": 120
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.socialProvider").value("KAKAO"))
                .andExpect(jsonPath("$.data.recommendedDailyTime").value(120));

        User savedUser = userRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "signup-1")
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(categoryRepository.countByUserId(savedUser.getId())).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(userNotificationSettingRepository.findByUserId(savedUser.getId()))
                .isPresent()
                .get()
                .satisfies(setting -> {
                    org.assertj.core.api.Assertions.assertThat(setting.isServiceAlarmEnabled()).isFalse();
                    org.assertj.core.api.Assertions.assertThat(setting.isThirtyMinPackAlarmEnabled()).isFalse();
                });
    }

    @Test
    void duplicateSocialSignupFails() throws Exception {
        userRepository.save(User.create(SocialProvider.KAKAO, "duplicate-1", "user@example.com", "서윤", null, 120));

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "duplicate-1",
                                  "socialToken": "verified:KAKAO:duplicate-1",
                                  "email": "other@example.com",
                                  "nickname": "다른사용자"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SOCIAL_ACCOUNT"));
    }

    @Test
    void socialSignupWithInvalidSocialTokenFailsAndDoesNotCreateUser() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "signup-invalid-token",
                                  "socialToken": "invalid-token",
                                  "email": "user@example.com",
                                  "nickname": "서윤"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        org.assertj.core.api.Assertions.assertThat(
                userRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "signup-invalid-token")
        ).isEmpty();
    }

    @Test
    void socialSignupWithProviderOrSocialIdMismatchFailsAndDoesNotCreateUser() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "signup-mismatch",
                                  "socialToken": "verified:KAKAO:different-social-id",
                                  "email": "user@example.com",
                                  "nickname": "서윤"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        org.assertj.core.api.Assertions.assertThat(
                userRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "signup-mismatch")
        ).isEmpty();
    }

    @Test
    void socialSignupWithUnsupportedProviderFails() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "NAVER",
                                  "socialId": "unsupported-1",
                                  "socialToken": "verified:NAVER:unsupported-1",
                                  "email": "user@example.com",
                                  "nickname": "서윤"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void socialLoginSuccessReturnsAccessToken() throws Exception {
        userRepository.save(User.create(SocialProvider.KAKAO, "login-1", "user@example.com", "서윤", null, 90));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "login-1",
                                  "socialToken": "verified:KAKAO:login-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.recommendedDailyTime").value(90));
    }

    @Test
    void tokenReissueSuccessRotatesRefreshToken() throws Exception {
        userRepository.save(User.create(SocialProvider.KAKAO, "reissue-1", "user@example.com", "서윤", null, 90));

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "reissue-1",
                                  "socialToken": "verified:KAKAO:reissue-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.data.refreshToken");

        String reissueResponse = mockMvc.perform(post("/api/auth/reissue")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rotatedRefreshToken = com.jayway.jsonpath.JsonPath.read(reissueResponse, "$.data.refreshToken");

        mockMvc.perform(post("/api/auth/reissue")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/auth/reissue")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(rotatedRefreshToken)))
                .andExpect(status().isOk());
    }

    @Test
    void socialLoginWithInvalidSocialTokenFails() throws Exception {
        userRepository.save(User.create(SocialProvider.KAKAO, "invalid-token-1", "user@example.com", "서윤", null, 90));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "invalid-token-1",
                                  "socialToken": "invalid-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void socialLoginWithUnknownAccountFails() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "unknown",
                                  "socialToken": "verified:KAKAO:unknown"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getMeSuccess() throws Exception {
        User user = userRepository.save(User.create(SocialProvider.KAKAO, "me-1", "user@example.com", "서윤", null, 150));
        String token = jwtTokenProvider.createAccessToken(user.getId());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.socialProvider").value("KAKAO"))
                .andExpect(jsonPath("$.data.recommendedDailyTime").value(150));
    }

    @Test
    void withdrawRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/users/me").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void withdrawSuccessDeletesUser() throws Exception {
        userRepository.save(User.create(SocialProvider.KAKAO, "withdraw-1", "user@example.com", "서윤", null, 150));

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "withdraw-1",
                                  "socialToken": "verified:KAKAO:withdraw-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.data.accessToken");

        mockMvc.perform(delete("/api/users/me")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void logoutDeletesRefreshToken() throws Exception {
        userRepository.save(User.create(SocialProvider.KAKAO, "logout-1", "user@example.com", "서윤", null, 90));

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "logout-1",
                                  "socialToken": "verified:KAKAO:logout-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.data.accessToken");
        String refreshToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.data.refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(post("/api/auth/reissue")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}

package com.cotato.blankit.domain.auth;

import com.cotato.blankit.domain.auth.repository.RefreshTokenRepository;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.support.AccessTokenTestFactory;
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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserNotificationSettingRepository userNotificationSettingRepository;

    @Autowired
    private AccessTokenTestFactory accessTokenTestFactory;

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
                                  "installationId": "signup-device",
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
        org.assertj.core.api.Assertions.assertThat(
                        categoryRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscCreatedAtAscIdAsc(savedUser.getId()))
                .extracting(Category::getName, Category::getColor)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("학업", "#FC5F5F"),
                        org.assertj.core.api.Assertions.tuple("일상", "#FF9A33"),
                        org.assertj.core.api.Assertions.tuple("기념일", "#FBF965")
                );
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
                                  "installationId": "duplicate-device",
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
                                  "installationId": "invalid-token-device",
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
                                  "installationId": "mismatch-device",
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
                                  "installationId": "unsupported-device",
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
                                  "socialToken": "verified:KAKAO:login-1",
                                  "installationId": "login-device"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.recommendedDailyTime").value(90));
    }

    @Test
    void socialLoginFromAnotherDeviceReplacesExistingSession() throws Exception {
        userRepository.save(User.create(SocialProvider.KAKAO, "single-device", "user@example.com", "서윤", null, 90));

        String firstLoginResponse = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("single-device", "device-a")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstAccessToken = com.jayway.jsonpath.JsonPath.read(firstLoginResponse, "$.data.accessToken");
        String firstRefreshToken = com.jayway.jsonpath.JsonPath.read(firstLoginResponse, "$.data.refreshToken");

        String secondLoginResponse = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("single-device", "device-b")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondAccessToken = com.jayway.jsonpath.JsonPath.read(secondLoginResponse, "$.data.accessToken");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(post("/api/auth/reissue")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(firstRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + secondAccessToken))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(refreshTokenRepository.findByUserId(
                        userRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "single-device")
                                .orElseThrow()
                                .getId()))
                .isPresent()
                .get()
                .extracting(com.cotato.blankit.domain.auth.entity.RefreshToken::getInstallationId)
                .isEqualTo("device-b");
    }

    @Test
    void socialLoginFromSameDeviceIsAllowed() throws Exception {
        userRepository.save(User.create(SocialProvider.KAKAO, "same-device", "user@example.com", "서윤", null, 90));

        String firstLoginResponse = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("same-device", "device-a")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstAccessToken = com.jayway.jsonpath.JsonPath.read(
                firstLoginResponse, "$.data.accessToken");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("same-device", "device-a")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void socialLoginRequiresInstallationId() throws Exception {
        userRepository.save(User.create(SocialProvider.KAKAO, "missing-device", "user@example.com", "서윤", null, 90));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "missing-device",
                                  "socialToken": "verified:KAKAO:missing-device"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
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
                                  "socialToken": "verified:KAKAO:reissue-1",
                                  "installationId": "reissue-device"
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
                                  "socialToken": "invalid-token",
                                  "installationId": "invalid-login-device"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void socialLoginWithMismatchedSocialIdFails() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "claimed-id",
                                  "socialToken": "verified:KAKAO:actual-id",
                                  "installationId": "mismatched-login-device"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void socialLoginWithUnknownAccountFails() throws Exception {
        long refreshTokenCountBeforeLogin = refreshTokenRepository.count();

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "socialProvider": "KAKAO",
                                  "socialId": "unknown",
                                  "socialToken": "verified:KAKAO:unknown",
                                  "installationId": "unknown-device"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("가입되지 않은 소셜 계정입니다."));

        org.assertj.core.api.Assertions.assertThat(
                        userRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "unknown"))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(refreshTokenRepository.count())
                .isEqualTo(refreshTokenCountBeforeLogin);
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
        String token = accessTokenTestFactory.createAccessToken(user.getId());

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
                                  "socialToken": "verified:KAKAO:withdraw-1",
                                  "installationId": "withdraw-device"
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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
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
                                  "socialToken": "verified:KAKAO:logout-1",
                                  "installationId": "logout-device"
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

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private String loginJson(String socialId, String installationId) {
        return """
                {
                  "socialProvider": "KAKAO",
                  "socialId": "%s",
                  "socialToken": "verified:KAKAO:%s",
                  "installationId": "%s"
                }
                """.formatted(socialId, socialId, installationId);
    }
}

package com.cotato.blankit.domain.user;

import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.user.service.UserService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class UserControllerTest {

    private MockMvc mockMvc;
    private User user;
    private String token;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserNotificationSettingRepository userNotificationSettingRepository;

    @Autowired
    private UserService userService;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        user = userRepository.save(User.create(SocialProvider.KAKAO, "timetable-settings-user", "user@example.com", "블랭킷", null, 120));
        token = jwtTokenProvider.createAccessToken(user.getId());
    }

    @Test
    void updateTimetableSettingsSavesToDb() throws Exception {
        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "09:00:00",
                                  "endTime": "23:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.timetableStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.timetableEndTime").value("23:00:00"));

        entityManager.flush();
        entityManager.clear();
        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getTimetableStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.getTimetableEndTime()).isEqualTo(LocalTime.of(23, 0));
    }

    @Test
    void updateTimetableSettingsDefaultValues() throws Exception {
        User freshUser = userRepository.save(User.create(SocialProvider.KAKAO, "fresh-settings-user", "fresh@example.com", "신규", null, 120));

        assertThat(freshUser.getTimetableStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(freshUser.getTimetableEndTime()).isEqualTo(LocalTime.of(0, 0));
    }

    @Test
    void updateTimetableSettingsRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "09:00:00",
                                  "endTime": "23:00:00"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void updateTimetableSettingsRejectsNullFields() throws Exception {
        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": null,
                                  "endTime": "23:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "09:00:00",
                                  "endTime": null
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTimetableSettingsRejectsReversedTimeRange() throws Exception {
        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "18:00:00",
                                  "endTime": "09:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMETABLE_SETTINGS"));
    }

    @Test
    void updateTimetableSettingsAllowsMidnightAsEndTime() throws Exception {
        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "08:00:00",
                                  "endTime": "00:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timetableEndTime").value("00:00:00"));

        entityManager.flush();
        entityManager.clear();
        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getTimetableEndTime()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void updateTimetableSettingsDoesNotAffectOtherUser() throws Exception {
        User otherUser = userRepository.save(User.create(SocialProvider.KAKAO, "other-settings-user", "other@example.com", "다른사용자", null, 120));

        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "10:00:00",
                                  "endTime": "22:00:00"
                                }
                                """))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        User unchanged = userRepository.findById(otherUser.getId()).orElseThrow();
        assertThat(unchanged.getTimetableStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(unchanged.getTimetableEndTime()).isEqualTo(LocalTime.of(0, 0));
    }

    @Test
    void getNotificationSettingsWithoutStoredSettingReturnsDefaultOff() throws Exception {
        mockMvc.perform(get("/api/users/me/notification-settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.isServiceAlarmEnabled").value(false))
                .andExpect(jsonPath("$.data.is30minPackAlarmEnabled").value(false));
    }

    @Test
    void updateNotificationSettingsSavesAndReturnsSettings() throws Exception {
        mockMvc.perform(patch("/api/users/me/notification-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isServiceAlarmEnabled": true,
                                  "is30minPackAlarmEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isServiceAlarmEnabled").value(true))
                .andExpect(jsonPath("$.data.is30minPackAlarmEnabled").value(false));

        entityManager.flush();
        entityManager.clear();

        UserNotificationSetting saved = userNotificationSettingRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(saved.isServiceAlarmEnabled()).isTrue();
        assertThat(saved.isThirtyMinPackAlarmEnabled()).isFalse();

        mockMvc.perform(get("/api/users/me/notification-settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isServiceAlarmEnabled").value(true))
                .andExpect(jsonPath("$.data.is30minPackAlarmEnabled").value(false));
    }

    @Test
    void updateNotificationSettingsCanTurnServiceAlarmOffAgain() throws Exception {
        UserNotificationSetting setting = UserNotificationSetting.createDefault(user);
        setting.update(true, false);
        userNotificationSettingRepository.save(setting);

        mockMvc.perform(patch("/api/users/me/notification-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isServiceAlarmEnabled": false,
                                  "is30minPackAlarmEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isServiceAlarmEnabled").value(false));

        entityManager.flush();
        entityManager.clear();

        assertThat(userNotificationSettingRepository.findByUserId(user.getId()))
                .isPresent()
                .get()
                .extracting(UserNotificationSetting::isServiceAlarmEnabled)
                .isEqualTo(false);
    }

    @Test
    void updateNotificationSettingsRejectsMissingFields() throws Exception {
        mockMvc.perform(patch("/api/users/me/notification-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isServiceAlarmEnabled": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notificationSettingsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me/notification-settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(patch("/api/users/me/notification-settings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isServiceAlarmEnabled": true,
                                  "is30minPackAlarmEnabled": false
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void serviceNotificationRecipientsIncludeOnlyEnabledUsers() {
        User enabledUser = userRepository.save(User.create(
                SocialProvider.KAKAO,
                "notification-enabled-user",
                "enabled@example.com",
                "수신동의",
                null,
                120
        ));
        User disabledUser = userRepository.save(User.create(
                SocialProvider.KAKAO,
                "notification-disabled-user",
                "disabled@example.com",
                "수신거부",
                null,
                120
        ));
        UserNotificationSetting enabledSetting = UserNotificationSetting.createDefault(enabledUser);
        enabledSetting.update(true, false);
        userNotificationSettingRepository.save(enabledSetting);
        userNotificationSettingRepository.save(UserNotificationSetting.createDefault(disabledUser));

        entityManager.flush();
        entityManager.clear();

        assertThat(userService.getServiceNotificationRecipientUserIds())
                .contains(enabledUser.getId())
                .doesNotContain(disabledUser.getId());
    }

    @Test
    void withdrawDeletesNotificationSetting() throws Exception {
        UserNotificationSetting setting = UserNotificationSetting.createDefault(user);
        setting.update(true, false);
        userNotificationSettingRepository.saveAndFlush(setting);

        mockMvc.perform(delete("/api/users/me")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        entityManager.flush();
        entityManager.clear();

        assertThat(userNotificationSettingRepository.findByUserId(user.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}

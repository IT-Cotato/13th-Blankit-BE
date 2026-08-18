package com.cotato.blankit.domain.user;

import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.timetable.entity.Timetable;
import com.cotato.blankit.domain.timetable.repository.TimetableRepository;
import com.cotato.blankit.domain.user.service.UserService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.support.AccessTokenTestFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    private AccessTokenTestFactory accessTokenTestFactory;

    @Autowired
    private UserNotificationSettingRepository userNotificationSettingRepository;

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private UserService userService;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        user = userRepository.save(User.create(
                SocialProvider.KAKAO,
                "timetable-settings-user-" + UUID.randomUUID(),
                "user@example.com",
                "블랭킷",
                null,
                120
        ));
        token = accessTokenTestFactory.createAccessToken(user.getId());
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

    // ── 시간표 표시 범위 - 블록 범위 초과 검증 ──────────────────────────

    @Test
    @DisplayName("블록이 새 표시 범위 안에 완전히 포함되면 변경에 성공한다")
    void updateTimetableSettings_blockFullyInsideRange_succeeds() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(17, 0), "강의");

        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "08:00:00",
                                  "endTime": "23:00:00"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("블록 startTime이 새 startTime과 같으면 (경계 포함) 변경에 성공한다")
    void updateTimetableSettings_blockStartAtBoundary_succeeds() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(17, 0), "강의");

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
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("블록 endTime이 새 endTime과 같으면 (경계 포함) 변경에 성공한다")
    void updateTimetableSettings_blockEndAtBoundary_succeeds() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(17, 0), "강의");

        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "08:00:00",
                                  "endTime": "17:00:00"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("블록 startTime이 새 startTime보다 이르면 TIMETABLE_SETTINGS_OUT_OF_RANGE 에러를 반환한다")
    void updateTimetableSettings_blockStartsBeforeNewRange_rejects() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(17, 0), "강의");

        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "10:00:00",
                                  "endTime": "23:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_SETTINGS_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("블록 endTime이 새 endTime보다 늦으면 TIMETABLE_SETTINGS_OUT_OF_RANGE 에러를 반환한다")
    void updateTimetableSettings_blockEndsAfterNewRange_rejects() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(17, 0), "강의");

        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "08:00:00",
                                  "endTime": "16:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_SETTINGS_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("endTime이 자정(00:00)이면 블록 endTime이 아무리 늦어도 상한 체크를 건너뛰어 성공한다")
    void updateTimetableSettings_midnightEndIgnoresBlockEndTime_succeeds() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(23, 0), "강의");

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
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("endTime이 자정이더라도 블록 startTime이 새 startTime보다 이르면 거절한다")
    void updateTimetableSettings_midnightEnd_blockStartsBefore_rejects() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(7, 0), LocalTime.of(23, 0), "강의");

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_SETTINGS_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("다른 유저의 블록이 범위 밖이어도 내 표시 범위 변경에는 영향을 주지 않는다")
    void updateTimetableSettings_otherUserBlockOutsideRange_doesNotAffect() throws Exception {
        User otherUser = userRepository.save(User.create(
                SocialProvider.KAKAO, "other-range-user-" + UUID.randomUUID(), "other2@example.com", "타인", null, null));
        saveTimetable(otherUser, (byte) 1, LocalTime.of(6, 0), LocalTime.of(20, 0), "타인강의");

        mockMvc.perform(patch("/api/users/me/timetable-settings")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "09:00:00",
                                  "endTime": "17:00:00"
                                }
                                """))
                .andExpect(status().isOk());
    }

    // ── 알림 설정 ──────────────────────────────────────────────────

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentInitialNotificationSettingUpdatesCreateOneSetting() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> responses = List.of(
                    submitNotificationSettingUpdate(executor, ready, start, true, false),
                    submitNotificationSettingUpdate(executor, ready, start, false, true)
            );

            ready.await();
            start.countDown();

            assertThat(responses)
                    .allSatisfy(response -> assertThat(response.get()).isEqualTo(200));
        }

        assertThat(userNotificationSettingRepository.countByUserId(user.getId())).isEqualTo(1);
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
        entityManager.clear();

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

    private Timetable saveTimetable(User owner, byte dayOfWeek, LocalTime startTime, LocalTime endTime, String title) {
        return timetableRepository.save(Timetable.create(owner, dayOfWeek, startTime, endTime, title, null, "#7B5EA7"));
    }

    private Future<Integer> submitNotificationSettingUpdate(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            boolean serviceAlarmEnabled,
            boolean thirtyMinPackAlarmEnabled
    ) {
        return executor.submit(() -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(patch("/api/users/me/notification-settings")
                            .with(csrf())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "isServiceAlarmEnabled": %s,
                                      "is30minPackAlarmEnabled": %s
                                    }
                                    """.formatted(serviceAlarmEnabled, thirtyMinPackAlarmEnabled)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        });
    }
}

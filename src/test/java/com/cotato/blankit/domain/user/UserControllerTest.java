package com.cotato.blankit.domain.user;

import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
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

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

        User unchanged = userRepository.findById(otherUser.getId()).orElseThrow();
        assertThat(unchanged.getTimetableStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(unchanged.getTimetableEndTime()).isEqualTo(LocalTime.of(0, 0));
    }
}

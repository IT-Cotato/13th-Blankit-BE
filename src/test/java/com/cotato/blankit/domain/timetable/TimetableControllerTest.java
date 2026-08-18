package com.cotato.blankit.domain.timetable;

import com.cotato.blankit.domain.timetable.entity.Timetable;
import com.cotato.blankit.domain.timetable.repository.TimetableRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.support.AccessTokenTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:timetable-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class TimetableControllerTest {

    private MockMvc mockMvc;
    private User user;
    private User otherUser;
    private String token;
    private String otherToken;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private AccessTokenTestFactory accessTokenTestFactory;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        user = userRepository.save(User.create(SocialProvider.KAKAO, "timetable-user", "user@example.com", "서윤", null, null));
        otherUser = userRepository.save(User.create(SocialProvider.KAKAO, "timetable-other", "other@example.com", "다른사용자", null, null));
        token = accessTokenTestFactory.createAccessToken(user.getId());
        otherToken = accessTokenTestFactory.createAccessToken(otherUser.getId());
    }

    // ── 목록 조회 ──────────────────────────────────────────────

    @Test
    void getTimetablesReturnsEmptyListWhenNoTimetables() throws Exception {
        mockMvc.perform(get("/api/timetable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getTimetablesReturnsSortedByDayOfWeekAndStartTime() throws Exception {
        saveTimetable(user, (byte) 3, LocalTime.of(13, 0), LocalTime.of(14, 30), "수요일 오후");
        saveTimetable(user, (byte) 1, LocalTime.of(13, 0), LocalTime.of(14, 30), "월요일 오후");
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "월요일 오전");

        mockMvc.perform(get("/api/timetable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].title").value("월요일 오전"))
                .andExpect(jsonPath("$.data[1].title").value("월요일 오후"))
                .andExpect(jsonPath("$.data[2].title").value("수요일 오후"));
    }

    @Test
    void getTimetablesDoesNotReturnOtherUsersData() throws Exception {
        saveTimetable(otherUser, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "타인 시간표");

        mockMvc.perform(get("/api/timetable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getTimetablesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/timetable"))
                .andExpect(status().isUnauthorized());
    }

    // ── 시간표 추가 ──────────────────────────────────────────────

    @Test
    void createTimetableSuccess() throws Exception {
        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "09:00:00",
                                    "endTime": "10:30:00",
                                    "title": "알고리즘 강의",
                                    "place": "공학관 101호",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.timetables[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.timetables[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.timetables[0].endTime").value("10:30:00"))
                .andExpect(jsonPath("$.data.timetables[0].title").value("알고리즘 강의"))
                .andExpect(jsonPath("$.data.timetables[0].place").value("공학관 101호"))
                .andExpect(jsonPath("$.data.timetables[0].color").value("#7B5EA7"))
                .andExpect(jsonPath("$.data.timetables[0].timetableId").exists());
    }

    @Test
    void createTimetableBulkSuccess() throws Exception {
        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "09:00:00",
                                    "endTime": "10:30:00",
                                    "title": "알고리즘 강의",
                                    "place": "공학관 101호",
                                    "color": "#7B5EA7"
                                  },
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "11:00:00",
                                    "endTime": "12:30:00",
                                    "title": "자료구조 강의",
                                    "place": "공학관 201호",
                                    "color": "#5C9EFF"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.timetables.length()").value(2))
                .andExpect(jsonPath("$.data.timetables[0].title").value("알고리즘 강의"))
                .andExpect(jsonPath("$.data.timetables[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.timetables[0].endTime").value("10:30:00"))
                .andExpect(jsonPath("$.data.timetables[0].timetableId").exists())
                .andExpect(jsonPath("$.data.timetables[1].title").value("자료구조 강의"))
                .andExpect(jsonPath("$.data.timetables[1].startTime").value("11:00:00"))
                .andExpect(jsonPath("$.data.timetables[1].endTime").value("12:30:00"))
                .andExpect(jsonPath("$.data.timetables[1].timetableId").exists());
    }

    @Test
    void createTimetableRejectsInvalidTimeRange() throws Exception {
        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "10:00:00",
                                    "endTime": "09:00:00",
                                    "title": "잘못된 시간",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_INVALID_TIME_RANGE"));
    }

    @Test
    void createTimetableRejectsSameStartAndEndTime() throws Exception {
        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "09:00:00",
                                    "endTime": "09:00:00",
                                    "title": "동일 시간",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMETABLE_INVALID_TIME_RANGE"));
    }

    @Test
    void createTimetableRejectsTimeConflict() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(11, 0), "기존 강의");

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "10:00:00",
                                    "endTime": "12:00:00",
                                    "title": "겹치는 강의",
                                    "color": "#5C9EFF"
                                  }
                                ]
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIMETABLE_TIME_CONFLICT"));
    }

    @Test
    void createTimetableAllowsSameDayNonOverlappingBlocks() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "1교시");

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "10:00:00",
                                    "endTime": "11:00:00",
                                    "title": "2교시",
                                    "color": "#5C9EFF"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void createTimetableAllowsOverlapOnDifferentDay() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(11, 0), "월요일 강의");

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 2,
                                    "startTime": "09:00:00",
                                    "endTime": "11:00:00",
                                    "title": "화요일 강의",
                                    "color": "#5C9EFF"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void createTimetableRejectsInvalidDayOfWeek() throws Exception {
        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 7,
                                    "startTime": "09:00:00",
                                    "endTime": "10:00:00",
                                    "title": "잘못된 요일",
                                    "color": "#5C9EFF"
                                  }
                                ]
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── 시간표 수정 ──────────────────────────────────────────────

    @Test
    void updateTimetableSuccess() throws Exception {
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "원래 제목");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정된 제목",
                                  "place": "수정된 장소"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timetable.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.timetable.place").value("수정된 장소"))
                .andExpect(jsonPath("$.data.timetable.dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.timetable.startTime").value("09:00:00"));
    }

    @Test
    void updateTimetableDoesNotConflictWithItself() throws Exception {
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "강의");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "09:30:00",
                                  "endTime": "11:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timetable.startTime").value("09:30:00"))
                .andExpect(jsonPath("$.data.timetable.endTime").value("11:00:00"));
    }

    @Test
    void updateTimetableRejectsTimeConflictWithOtherBlock() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(13, 0), LocalTime.of(14, 30), "오후 강의");
        Timetable target = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "오전 강의");

        mockMvc.perform(patch("/api/timetable/{timetableId}", target.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "12:00:00",
                                  "endTime": "14:00:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIMETABLE_TIME_CONFLICT"));
    }

    @Test
    void updateTimetableReturnsNotFoundForOtherUsersTimetable() throws Exception {
        Timetable otherTimetable = saveTimetable(otherUser, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "타인 시간표");

        mockMvc.perform(patch("/api/timetable/{timetableId}", otherTimetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정되면 안 됨"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIMETABLE_NOT_FOUND"));
    }

    @Test
    void updateTimetableReturnsNotFoundForNonexistentId() throws Exception {
        mockMvc.perform(patch("/api/timetable/{timetableId}", 999999L)
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "없는 시간표"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIMETABLE_NOT_FOUND"));
    }

    @Test
    void updateTimetableRejectsEmptyPlace() throws Exception {
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "원래 제목");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "place": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTimetableRejectsWhitespacePlace() throws Exception {
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "원래 제목");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "place": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTimetableRejectsEmptyTitle() throws Exception {
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "원래 제목");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTimetableRejectsWhitespaceTitle() throws Exception {
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "원래 제목");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTimetableRejectsEmptyColor() throws Exception {
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "원래 제목");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "color": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTimetableRejectsInvalidHexColor() throws Exception {
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 30), "원래 제목");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "color": "red"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── 시간표 단건 삭제 ──────────────────────────────────────────

    @Test
    void deleteTimetableSuccess() throws Exception {
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "삭제할 강의");

        mockMvc.perform(delete("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                timetableRepository.findByTimetableIdAndUserId(timetable.getTimetableId(), user.getId())
        ).isEmpty();
    }

    @Test
    void deleteTimetableOnlyDeletesTargetBlock() throws Exception {
        Timetable target = saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "삭제 대상");
        Timetable other = saveTimetable(user, (byte) 1, LocalTime.of(11, 0), LocalTime.of(12, 0), "유지 대상");

        mockMvc.perform(delete("/api/timetable/{timetableId}", target.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                timetableRepository.findByTimetableIdAndUserId(other.getTimetableId(), user.getId())
        ).isPresent();
    }

    @Test
    void deleteTimetableReturnsNotFoundForOtherUsersTimetable() throws Exception {
        Timetable otherTimetable = saveTimetable(otherUser, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "타인 시간표");

        mockMvc.perform(delete("/api/timetable/{timetableId}", otherTimetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIMETABLE_NOT_FOUND"));

        org.assertj.core.api.Assertions.assertThat(
                timetableRepository.findByTimetableIdAndUserId(otherTimetable.getTimetableId(), otherUser.getId())
        ).isPresent();
    }

    // ── 시간표 전체 초기화 ────────────────────────────────────────

    @Test
    void deleteAllTimetablesClearsOnlyCurrentUsersData() throws Exception {
        saveTimetable(user, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "내 강의1");
        saveTimetable(user, (byte) 3, LocalTime.of(13, 0), LocalTime.of(14, 0), "내 강의2");
        Timetable otherTimetable = saveTimetable(otherUser, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "타인 강의");

        mockMvc.perform(delete("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/timetable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        org.assertj.core.api.Assertions.assertThat(
                timetableRepository.findByTimetableIdAndUserId(otherTimetable.getTimetableId(), otherUser.getId())
        ).isPresent();
    }

    // ── 표시 범위 벗어난 블록 등록/수정 거절 ─────────────────────────────

    @Test
    @DisplayName("블록 startTime이 표시 범위보다 이르면 displayStart를 올림한 정각으로 당겨 확장한다")
    void createTimetableExpandsDisplayStartWhenBlockStartsBeforeRange() throws Exception {
        user.updateTimetableSettings(LocalTime.of(10, 0), LocalTime.of(22, 0));

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "09:00:00",
                                    "endTime": "11:00:00",
                                    "title": "범위 밖 강의",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(LocalTime.of(9, 0), updated.getTimetableStartTime());
        assertEquals(LocalTime.of(22, 0), updated.getTimetableEndTime());
    }

    @Test
    @DisplayName("블록 endTime이 표시 범위보다 늦으면 displayEnd를 올림한 정각으로 확장한다")
    void createTimetableExpandsDisplayEndWhenBlockExceedsRange() throws Exception {
        user.updateTimetableSettings(LocalTime.of(8, 0), LocalTime.of(20, 0));

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "19:00:00",
                                    "endTime": "21:00:00",
                                    "title": "범위 밖 강의",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(LocalTime.of(8, 0), updated.getTimetableStartTime());
        assertEquals(LocalTime.of(21, 0), updated.getTimetableEndTime());
    }

    @Test
    @DisplayName("표시 범위 endTime이 자정(00:00)이면 블록 endTime 상한을 체크하지 않는다")
    void createTimetableAllowsBlockWhenDisplayEndIsMidnight() throws Exception {
        user.updateTimetableSettings(LocalTime.of(8, 0), LocalTime.MIDNIGHT);

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "22:00:00",
                                    "endTime": "23:30:00",
                                    "title": "늦은 강의",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("수정 후 블록 startTime이 표시 범위보다 이르면 displayStart를 올림한 정각으로 당겨 확장한다")
    void updateTimetableExpandsDisplayStartWhenBlockStartsBeforeRange() throws Exception {
        user.updateTimetableSettings(LocalTime.of(10, 0), LocalTime.of(22, 0));
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(11, 0), LocalTime.of(13, 0), "강의");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "09:00:00",
                                  "endTime": "11:00:00"
                                }
                                """))
                .andExpect(status().isOk());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(LocalTime.of(9, 0), updated.getTimetableStartTime());
        assertEquals(LocalTime.of(22, 0), updated.getTimetableEndTime());
    }

    @Test
    @DisplayName("수정 후 블록 endTime이 표시 범위를 넘으면 displayEnd를 올림한 정각으로 확장한다")
    void updateTimetableExpandsDisplayEndWhenBlockExceedsRange() throws Exception {
        user.updateTimetableSettings(LocalTime.of(10, 0), LocalTime.of(22, 0));
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(11, 0), LocalTime.of(13, 0), "강의");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "endTime": "23:00:00"
                                }
                                """))
                .andExpect(status().isOk());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(LocalTime.of(10, 0), updated.getTimetableStartTime());
        assertEquals(LocalTime.of(23, 0), updated.getTimetableEndTime());
    }

    @Test
    @DisplayName("표시 범위 endTime이 자정이면 블록 endTime을 늦게 수정해도 성공한다")
    void updateTimetableAllowsLaterEndTimeWhenDisplayEndIsMidnight() throws Exception {
        user.updateTimetableSettings(LocalTime.of(8, 0), LocalTime.MIDNIGHT);
        Timetable timetable = saveTimetable(user, (byte) 1, LocalTime.of(11, 0), LocalTime.of(13, 0), "강의");

        mockMvc.perform(patch("/api/timetable/{timetableId}", timetable.getTimetableId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "endTime": "23:30:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timetable.endTime").value("23:30:00"));
    }

    // ── 표시 범위 확장 올림/내림 검증 ──────────────────────────────────

    @Test
    @DisplayName("블록 startTime이 비정각이면 내림하여 displayStart를 확장한다 (07:30 → 07:00)")
    void expandDisplayStartFloorsToHourWhenBlockStartHasMinutes() throws Exception {
        user.updateTimetableSettings(LocalTime.of(8, 0), LocalTime.of(20, 0));

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "07:30:00",
                                    "endTime": "09:00:00",
                                    "title": "이른 강의",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(LocalTime.of(7, 0), updated.getTimetableStartTime());
        assertEquals(LocalTime.of(20, 0), updated.getTimetableEndTime());
    }

    @Test
    @DisplayName("블록 startTime이 정각이면 그 시각 그대로 displayStart를 확장한다 (07:00 → 07:00)")
    void expandDisplayStartKeepsExactHourWhenBlockStartIsOnHour() throws Exception {
        user.updateTimetableSettings(LocalTime.of(9, 0), LocalTime.of(20, 0));

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "07:00:00",
                                    "endTime": "09:00:00",
                                    "title": "이른 강의",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(LocalTime.of(7, 0), updated.getTimetableStartTime());
        assertEquals(LocalTime.of(20, 0), updated.getTimetableEndTime());
    }

    @Test
    @DisplayName("블록 endTime이 비정각이면 올림하여 displayEnd를 확장한다 (19:50 → 20:00)")
    void expandDisplayEndCeilsToNextHourWhenBlockEndHasMinutes() throws Exception {
        user.updateTimetableSettings(LocalTime.of(8, 0), LocalTime.of(17, 0));

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "16:00:00",
                                    "endTime": "19:50:00",
                                    "title": "늦은 강의",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(LocalTime.of(8, 0), updated.getTimetableStartTime());
        assertEquals(LocalTime.of(20, 0), updated.getTimetableEndTime());
    }

    @Test
    @DisplayName("블록 endTime이 정각이면 그 시각 그대로 displayEnd를 확장한다 (20:00 → 20:00)")
    void expandDisplayEndKeepsExactHourWhenBlockEndIsOnHour() throws Exception {
        user.updateTimetableSettings(LocalTime.of(8, 0), LocalTime.of(18, 0));

        mockMvc.perform(post("/api/timetable")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "dayOfWeek": 1,
                                    "startTime": "16:00:00",
                                    "endTime": "20:00:00",
                                    "title": "늦은 강의",
                                    "color": "#7B5EA7"
                                  }
                                ]
                                """))
                .andExpect(status().isCreated());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(LocalTime.of(8, 0), updated.getTimetableStartTime());
        assertEquals(LocalTime.of(20, 0), updated.getTimetableEndTime());
    }

    // ── helper ───────────────────────────────────────────────────

    private Timetable saveTimetable(User owner, byte dayOfWeek, LocalTime startTime, LocalTime endTime, String title) {
        return timetableRepository.save(Timetable.create(owner, dayOfWeek, startTime, endTime, title, null, "#7B5EA7"));
    }
}

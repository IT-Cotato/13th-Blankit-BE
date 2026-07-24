package com.cotato.blankit.domain.feedback;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.playlist.entity.Playlist;
import com.cotato.blankit.domain.playlist.entity.PlaylistItem;
import com.cotato.blankit.domain.playlist.repository.PlaylistItemRepository;
import com.cotato.blankit.domain.playlist.repository.PlaylistRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskSessionRepository taskSessionRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private PlaylistItemRepository playlistItemRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

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
        Category category = categoryRepository.save(Category.create(user, "학업", "#5C9EFF", "book", 0, true));
        taskA = taskRepository.save(Task.create(user, category, "과업A", LocalDate.of(2026, 7, 31), null));
        taskB = taskRepository.save(Task.create(user, category, "과업B", LocalDate.of(2026, 7, 31), null));
    }

    // ─── 세션 생성 ────────────────────────────────────────────────────────────

    @Test
    void createSession_initialStatusIsPaused() throws Exception {
        // 세션 생성 직후 상태는 PAUSED, elapsedTime=0, endedAt 없음
        mockMvc.perform(post("/api/tasks/{taskId}/sessions", taskA.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.elapsedTime").value(0))
                .andExpect(jsonPath("$.data.endedAt").doesNotExist());
    }

    // ─── 활성 세션 조회 ────────────────────────────────────────────────────────

    @Test
    void getActiveSession_noSession_returnsNull() throws Exception {
        // 과업에 세션이 없으면 data=null 반환
        mockMvc.perform(get("/api/tasks/{taskId}/sessions/active", taskA.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getActiveSession_pausedSession_returnsSession() throws Exception {
        // PAUSED 세션은 활성(DONE이 아닌) 세션으로 조회됨
        taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 300, TaskSessionStatus.PAUSED));

        mockMvc.perform(get("/api/tasks/{taskId}/sessions/active", taskA.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.elapsedTime").value(300));
    }

    // ─── 세션 상태 변경 ───────────────────────────────────────────────────────

    @Test
    void updateStatus_toPlaying_noOtherPlayingSession_succeeds() throws Exception {
        // 다른 PLAYING 세션이 없을 때 PAUSED → PLAYING 전환 정상 처리
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 0, TaskSessionStatus.PAUSED));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PLAYING", "elapsedTime": 0 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLAYING"));
    }

    @Test
    void updateStatus_toPlaying_sameSessionAlreadyPlaying_returns409() throws Exception {
        // 이미 PLAYING 상태인 세션에 PLAYING 요청 → SESSION_ALREADY_PLAYING(409)
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 300, TaskSessionStatus.PLAYING));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PLAYING", "elapsedTime": 400 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_PLAYING"));
    }

    @Test
    void updateStatus_toPlaying_otherTaskAlreadyPlaying_returns409() throws Exception {
        // 다른 과업(taskA)의 세션이 PLAYING 중일 때 taskB 세션 PLAYING 요청 → 409
        taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 300, TaskSessionStatus.PLAYING));
        TaskSession sessionB = taskSessionRepository.save(
                TaskSession.create(taskB, user, LocalDateTime.now(), null, 0, TaskSessionStatus.PAUSED));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", sessionB.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PLAYING", "elapsedTime": 0 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_PLAYING"));
    }

    @Test
    void updateStatus_toPaused_savesElapsedTime() throws Exception {
        // PLAYING → PAUSED 전환 시 클라이언트가 보낸 elapsedTime이 서버에 저장됨
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 300, TaskSessionStatus.PLAYING));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PAUSED", "elapsedTime": 600 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.elapsedTime").value(600));
    }

    @Test
    void updateStatus_toDone_recordsEndedAt() throws Exception {
        // DONE 처리 시 endedAt이 기록됨 (세션 종료 시각)
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 1800, TaskSessionStatus.PLAYING));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "DONE", "elapsedTime": 1800 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.endedAt").isNotEmpty());
    }

    @Test
    void updateStatus_smallerElapsedTime_doesNotDecreaseElapsedTime() throws Exception {
        // 지연/역순 요청으로 더 작은 elapsedTime이 오면 기존 값이 유지됨
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 600, TaskSessionStatus.PLAYING));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PAUSED", "elapsedTime": 300 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.elapsedTime").value(600));
    }

    @Test
    void submitFeedback_duplicateSession_throwsConstraintViolation() {
        // 동일 세션에 피드백을 두 번 직접 저장하면 unique 제약 위반 발생
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 0, TaskSessionStatus.PAUSED));
        feedbackRepository.saveAndFlush(Feedback.create(session, taskA, user, 50, null, true));

        assertThatThrownBy(() ->
                feedbackRepository.saveAndFlush(Feedback.create(session, taskA, user, 60, null, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ─── 피드백 ───────────────────────────────────────────────────────────────

    @Test
    void updateStatus_doneSession_returns409() throws Exception {
        // 이미 DONE 처리된 세션에 PLAYING/PAUSED 요청 시 SESSION_ALREADY_DONE(409)
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 1800, TaskSessionStatus.DONE));

        mockMvc.perform(patch("/api/sessions/{sessionId}/status", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PLAYING", "elapsedTime": 1800 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_DONE"));
    }

    @Test
    void getFeedback_noFeedback_returnsNull() throws Exception {
        // 피드백이 없는 세션 조회 시 data=null 반환
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 0, TaskSessionStatus.PAUSED));

        mockMvc.perform(get("/api/sessions/{sessionId}/feedback", session.getTaskSessionId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void saveFeedback_draft_thenGetFeedback_returnsDraftContent() throws Exception {
        // isDraft=true로 임시저장 후 조회 시 저장된 progressRate·memo·isDraft 그대로 반환
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 300, TaskSessionStatus.PAUSED));

        mockMvc.perform(post("/api/sessions/{sessionId}/feedback", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "progressRate": 30, "memo": "여기까지 완료", "isDraft": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDraft").value(true))
                .andExpect(jsonPath("$.data.progressRate").value(30));

        mockMvc.perform(get("/api/sessions/{sessionId}/feedback", session.getTaskSessionId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressRate").value(30))
                .andExpect(jsonPath("$.data.memo").value("여기까지 완료"))
                .andExpect(jsonPath("$.data.isDraft").value(true));
    }

    @Test
    void saveFeedback_final_updatesEstimatedTime() throws Exception {
        // isDraft=false 최종 제출 시 estimatedTime 보정 로직이 실행되어 기존값과 달라짐
        taskA.updateEstimatedTime(120);
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 1800, TaskSessionStatus.PAUSED));

        mockMvc.perform(post("/api/sessions/{sessionId}/feedback", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "progressRate": 50, "memo": null, "isDraft": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDraft").value(false))
                .andExpect(jsonPath("$.data.progressRate").value(50));

        Task updated = taskRepository.findById(taskA.getId()).orElseThrow();
        assertThat(updated.getEstimatedTime()).isNotEqualTo(120);
    }

    @Test
    void createSession_existingPausedSession_returnsExistingWithoutCreatingNew() throws Exception {
        // PAUSED 세션이 이미 있으면 새 세션을 생성하지 않고 기존 세션을 반환
        TaskSession existing = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 300, TaskSessionStatus.PAUSED));

        mockMvc.perform(post("/api/tasks/{taskId}/sessions", taskA.getId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.taskSessionId").value(existing.getTaskSessionId()))
                .andExpect(jsonPath("$.data.elapsedTime").value(300));

        assertThat(taskSessionRepository.count()).isEqualTo(1);
    }

    @Test
    void submitFeedback_progressRate100_taskStatusBecomeDone() throws Exception {
        // 진행률 100% 최종 제출 시 과업 상태가 DONE으로 변경됨
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 3600, TaskSessionStatus.PAUSED));

        mockMvc.perform(post("/api/sessions/{sessionId}/feedback", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "progressRate": 100, "memo": null, "isDraft": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressRate").value(100));

        Task updated = taskRepository.findById(taskA.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void submitFeedback_progressRate100_removedFromPlaylist() throws Exception {
        // 진행률 100% 최종 제출 시 플레이리스트에서 해당 과업 항목만 삭제됨
        Playlist playlist = playlistRepository.save(Playlist.create(user));
        PlaylistItem itemA = playlistItemRepository.save(PlaylistItem.create(playlist, taskA, 0, null));
        playlistItemRepository.save(PlaylistItem.create(playlist, taskB, 1, null));

        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 3600, TaskSessionStatus.PAUSED));

        mockMvc.perform(post("/api/sessions/{sessionId}/feedback", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "progressRate": 100, "memo": null, "isDraft": false }
                                """))
                .andExpect(status().isOk());

        assertThat(playlistItemRepository.findById(itemA.getPlaylistItemId())).isEmpty();
        assertThat(playlistItemRepository.countByPlaylist(playlist)).isEqualTo(1);
    }

    @Test
    void submitFeedback_draftThenFinalSubmit_overwritesAndFinalizes() throws Exception {
        // 임시저장(isDraft=true) 후 최종 제출(isDraft=false) 시 동일 피드백 레코드가 업데이트됨
        TaskSession session = taskSessionRepository.save(
                TaskSession.create(taskA, user, LocalDateTime.now(), null, 1800, TaskSessionStatus.PAUSED));

        mockMvc.perform(post("/api/sessions/{sessionId}/feedback", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "progressRate": 40, "memo": "중간", "isDraft": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDraft").value(true));

        mockMvc.perform(post("/api/sessions/{sessionId}/feedback", session.getTaskSessionId())
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "progressRate": 50, "memo": "최종", "isDraft": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDraft").value(false))
                .andExpect(jsonPath("$.data.progressRate").value(50));

        assertThat(feedbackRepository.count()).isEqualTo(1);
    }
}

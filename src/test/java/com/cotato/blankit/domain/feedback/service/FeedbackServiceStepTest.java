package com.cotato.blankit.domain.feedback.service;

import com.cotato.blankit.domain.feedback.dto.request.FeedbackSubmitRequest;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.playlist.repository.PlaylistItemRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.service.TaskStepService;
import com.cotato.blankit.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceStepTest {

    @Mock FeedbackRepository feedbackRepository;
    @Mock TaskSessionRepository taskSessionRepository;
    @Mock TaskSessionService taskSessionService;
    @Mock PlaylistItemRepository playlistItemRepository;
    @Mock EstimatedTimeCalculator calculator;
    @Mock TaskStepService taskStepService;
    @Mock Clock clock;
    @InjectMocks FeedbackService feedbackService;

    @Mock TaskSession session;
    @Mock Task task;
    @Mock User user;
    @Mock Feedback existingFeedback;
    @Mock TaskSession feedbackTaskSession;

    private static final Long USER_ID = 99L;
    private static final Long SESSION_ID = 1L;
    private static final Long TASK_ID = 10L;

    @BeforeEach
    void setUp() {
        given(taskSessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(session.getUser()).willReturn(user);
        given(user.getId()).willReturn(USER_ID);
        given(session.getTask()).willReturn(task);
        given(task.getId()).willReturn(TASK_ID);

        // 기존 피드백이 있어 새로 생성하지 않는 경로로 진행
        given(feedbackRepository.findByTaskSessionAndIsDraftTrue(session)).willReturn(Optional.of(existingFeedback));

        // FeedbackResponse.from(existingFeedback) 에 필요한 스텁
        given(existingFeedback.getTaskSession()).willReturn(feedbackTaskSession);
        given(existingFeedback.getTask()).willReturn(task);

        given(clock.instant()).willReturn(Instant.parse("2026-08-05T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
    }

    // ─── 콘텐츠 필수 검증 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("콘텐츠 필수 검증")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class ContentRequired {

        @Test
        @DisplayName("progressRate·memo·steps가 모두 없으면 FEEDBACK_CONTENT_REQUIRED 예외가 발생하고 세션 조회를 하지 않는다")
        void submitFeedback_noContent_throwsFeedbackContentRequired() {
            // given
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(null, null, false, null);

            // when & then
            assertThatThrownBy(() -> feedbackService.submitFeedback(USER_ID, SESSION_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FEEDBACK_CONTENT_REQUIRED);
            then(taskSessionRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("memo가 공백 문자열이고 나머지가 없으면 FEEDBACK_CONTENT_REQUIRED 예외가 발생한다")
        void submitFeedback_blankMemoOnly_throwsFeedbackContentRequired() {
            // given
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(null, "   ", false, null);

            // when & then
            assertThatThrownBy(() -> feedbackService.submitFeedback(USER_ID, SESSION_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FEEDBACK_CONTENT_REQUIRED);
        }

        @Test
        @DisplayName("steps가 빈 리스트이고 나머지가 없으면 FEEDBACK_CONTENT_REQUIRED 예외가 발생한다")
        void submitFeedback_emptyStepsOnly_throwsFeedbackContentRequired() {
            // given
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(null, null, false, List.of());

            // when & then
            assertThatThrownBy(() -> feedbackService.submitFeedback(USER_ID, SESSION_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FEEDBACK_CONTENT_REQUIRED);
        }
    }

    // ─── steps 없음 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("steps가 없으면 request.progressRate를 그대로 사용한다")
    class NoSteps {

        @Test
        @DisplayName("steps=null이면 applyAndCalculateProgress를 호출하지 않는다")
        void submitFeedback_stepsNull_doesNotCallApply() {
            // given
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(40, "메모", true, null);

            // when
            feedbackService.submitFeedback(USER_ID, SESSION_ID, request);

            // then
            then(taskStepService).should(never()).applyAndCalculateProgress(any(), any());
        }

        @Test
        @DisplayName("steps=빈 리스트이면 applyAndCalculateProgress를 호출하지 않는다")
        void submitFeedback_stepsEmpty_doesNotCallApply() {
            // given
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(40, "메모", true, List.of());

            // when
            feedbackService.submitFeedback(USER_ID, SESSION_ID, request);

            // then
            then(taskStepService).should(never()).applyAndCalculateProgress(any(), any());
        }
    }

    // ─── steps 있음 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("steps가 있으면 applyAndCalculateProgress를 통해 진행률을 계산한다")
    class WithSteps {

        @Test
        @DisplayName("stepId와 progressRate가 올바른 Map으로 변환되어 applyAndCalculateProgress가 호출된다")
        void submitFeedback_stepsPresent_callsApplyWithCorrectMap() {
            // given
            List<FeedbackSubmitRequest.StepProgressItem> steps = List.of(
                    new FeedbackSubmitRequest.StepProgressItem(1L, 100),
                    new FeedbackSubmitRequest.StepProgressItem(2L, 50),
                    new FeedbackSubmitRequest.StepProgressItem(3L, 0)
            );
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(null, null, true, steps);
            given(taskStepService.applyAndCalculateProgress(any(), any())).willReturn(50);

            // when
            feedbackService.submitFeedback(USER_ID, SESSION_ID, request);

            // then
            ArgumentCaptor<Map<Long, Integer>> mapCaptor = ArgumentCaptor.forClass(Map.class);
            then(taskStepService).should().applyAndCalculateProgress(any(), mapCaptor.capture());
            assertThat(mapCaptor.getValue())
                    .containsEntry(1L, 100)
                    .containsEntry(2L, 50)
                    .containsEntry(3L, 0);
        }

        @Test
        @DisplayName("isDraft=false일 때 계산된 진행률로 과업 진행률이 갱신된다")
        void submitFeedback_stepsPresent_isDraftFalse_updatesTaskProgress() {
            // given
            List<FeedbackSubmitRequest.StepProgressItem> steps = List.of(
                    new FeedbackSubmitRequest.StepProgressItem(1L, 60),
                    new FeedbackSubmitRequest.StepProgressItem(2L, 40)
            );
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(null, null, false, steps);
            given(taskStepService.applyAndCalculateProgress(any(), any())).willReturn(50);

            // updateEstimatedTime 경로 스텁
            given(taskSessionRepository.sumElapsedTimeByTaskIdAndUserId(TASK_ID, USER_ID)).willReturn(300L);
            given(feedbackRepository.findByTask_IdAndIsDraftFalseOrderByCreatedAtAsc(TASK_ID)).willReturn(List.of());
            given(task.getSimilarTask()).willReturn(null);
            given(calculator.calculate(any(), anyInt(), anyLong(), any(), any())).willReturn(120);

            // when
            feedbackService.submitFeedback(USER_ID, SESSION_ID, request);

            // then: applyAndCalculateProgress가 반환한 50이 task 진행률 갱신에 사용됨
            then(task).should().updateProgressRate(50);
            then(taskSessionService).should().completeSession(any(), any());
        }

        @Test
        @DisplayName("isDraft=true이면 applyAndCalculateProgress를 호출하지만 세션 완료와 진행률 갱신은 하지 않는다")
        void submitFeedback_stepsPresent_isDraftTrue_doesNotCompleteOrUpdateProgress() {
            // given
            List<FeedbackSubmitRequest.StepProgressItem> steps = List.of(
                    new FeedbackSubmitRequest.StepProgressItem(1L, 60)
            );
            FeedbackSubmitRequest request = new FeedbackSubmitRequest(null, null, true, steps);
            given(taskStepService.applyAndCalculateProgress(any(), any())).willReturn(60);

            // when
            feedbackService.submitFeedback(USER_ID, SESSION_ID, request);

            // then
            then(taskStepService).should().applyAndCalculateProgress(any(), any());
            then(taskSessionService).should(never()).completeSession(any(), any());
            then(task).should(never()).updateProgressRate(any());
        }
    }
}

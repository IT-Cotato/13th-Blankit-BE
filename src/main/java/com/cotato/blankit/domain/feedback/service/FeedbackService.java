package com.cotato.blankit.domain.feedback.service;

import com.cotato.blankit.domain.feedback.dto.request.FeedbackSubmitRequest;
import com.cotato.blankit.domain.feedback.dto.response.FeedbackResponse;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.playlist.repository.PlaylistItemRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final TaskSessionRepository taskSessionRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final EstimatedTimeCalculator calculator;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FeedbackResponse getFeedback(Long userId, Long sessionId) {
        TaskSession session = getSessionAndVerifyOwner(userId, sessionId);
        return feedbackRepository.findByTaskSessionAndIsDraftTrue(session)
                .or(() -> feedbackRepository.findByTaskSessionAndIsDraftFalse(session))
                .map(FeedbackResponse::from)
                .orElse(null);
    }

    @Transactional
    public FeedbackResponse submitFeedback(Long userId, Long sessionId, FeedbackSubmitRequest request) {
        TaskSession session = getSessionAndVerifyOwner(userId, sessionId);
        Feedback feedback = feedbackRepository.findByTaskSessionAndIsDraftTrue(session)
                .or(() -> feedbackRepository.findByTaskSessionAndIsDraftFalse(session))
                .orElseGet(() -> {
                    try {
                        return feedbackRepository.saveAndFlush(
                                Feedback.create(session, session.getTask(), session.getUser(),
                                        request.progressRate(), request.memo(), request.isDraft()));
                    } catch (DataIntegrityViolationException e) {
                        if (e.getMessage() != null && e.getMessage().contains("uk_feedback_task_session")) {
                            throw new CustomException(ErrorCode.FEEDBACK_DUPLICATE);
                        }
                        throw e;
                    }
                });
        feedback.update(request.progressRate(), request.memo(), request.isDraft());
        if (!request.isDraft()) {
            session.updateStatus(TaskSessionStatus.DONE, clock);
            if (request.progressRate() != null && request.progressRate() == 100) {
                feedback.complete();
                Task task = session.getTask();
                task.updateStatus(TaskStatus.DONE);
                playlistItemRepository.deleteByTask(task);
            }
        }
        if (!request.isDraft() && request.progressRate() != null && request.progressRate() > 0) {
            updateEstimatedTime(userId, feedback, session.getTask());
            session.getTask().updateProgressRate(request.progressRate());
        }
        return FeedbackResponse.from(feedback);
    }

    private void updateEstimatedTime(Long userId, Feedback feedback, Task task) {
        long cumulativeElapsedSeconds = taskSessionRepository.sumElapsedTimeByTaskIdAndUserId(task.getId(), userId);

        List<Feedback> previousFeedbacks = feedbackRepository
                .findByTask_IdAndIsDraftFalseOrderByCreatedAtAsc(task.getId())
                .stream()
                .filter(f -> !f.getFeedbackId().equals(feedback.getFeedbackId()))
                .filter(f -> f.getProgressRate() != null)
                .toList();

        List<Feedback> similarTaskFeedbacks = task.getSimilarTask() != null
                ? feedbackRepository.findByTask_IdAndIsDraftFalseOrderByCreatedAtAsc(task.getSimilarTask().getId())
                        .stream()
                        .filter(f -> f.getProgressRate() != null)
                        .toList()
                : List.of();

        int newEstimatedMinutes = calculator.calculate(task, feedback.getProgressRate(),
                cumulativeElapsedSeconds, previousFeedbacks, similarTaskFeedbacks);
        task.updateEstimatedTime(newEstimatedMinutes);

        Feedback prevFeedback = previousFeedbacks.isEmpty() ? null : previousFeedbacks.get(previousFeedbacks.size() - 1);
        int prevRate = prevFeedback != null ? prevFeedback.getProgressRate() : 0;
        long prevCumulative = prevFeedback != null ? prevFeedback.getCumulativeElapsedTime() : 0;
        long intervalElapsedCurrent = cumulativeElapsedSeconds - prevCumulative;

        Integer consecutiveCount = null;
        Integer intervalDiff = null;
        if (!similarTaskFeedbacks.isEmpty()) {
            long similarAtCurrent = calculator.getSimilarElapsedSeconds(similarTaskFeedbacks, feedback.getProgressRate());
            long similarAtPrev = calculator.getSimilarElapsedSeconds(similarTaskFeedbacks, prevRate);
            long B = intervalElapsedCurrent - (similarAtCurrent - similarAtPrev);
            consecutiveCount = calculator.calculateCount(B, prevFeedback);
            intervalDiff = (int) B;
        }

        feedback.updateMetrics(prevRate, (int) cumulativeElapsedSeconds, consecutiveCount, intervalDiff);
    }

    private TaskSession getSessionAndVerifyOwner(Long userId, Long sessionId) {
        TaskSession session = taskSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }
}

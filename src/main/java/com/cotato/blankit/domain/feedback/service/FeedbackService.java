package com.cotato.blankit.domain.feedback.service;

import com.cotato.blankit.domain.feedback.dto.request.FeedbackSubmitRequest;
import com.cotato.blankit.domain.feedback.dto.response.FeedbackResponse;
import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.repository.FeedbackRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final TaskSessionRepository taskSessionRepository;

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
                .orElseGet(() -> feedbackRepository.save(
                        Feedback.create(session, session.getTask(), session.getUser(),
                                request.progressRate(), request.memo(), request.isDraft())
                ));
        feedback.update(request.progressRate(), request.memo(), request.isDraft());
        if (!request.isDraft() && request.progressRate() != null && request.progressRate() == 100) {
            feedback.complete();
        }
        return FeedbackResponse.from(feedback);
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

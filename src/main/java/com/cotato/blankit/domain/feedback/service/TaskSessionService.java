package com.cotato.blankit.domain.feedback.service;

import com.cotato.blankit.domain.feedback.dto.request.SessionStatusUpdateRequest;
import com.cotato.blankit.domain.feedback.dto.response.TaskSessionResponse;
import com.cotato.blankit.domain.feedback.entity.DailyElapsedTime;
import com.cotato.blankit.domain.feedback.entity.PlayInterval;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.repository.DailyElapsedTimeRepository;
import com.cotato.blankit.domain.feedback.repository.PlayIntervalRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskSessionService {

    private final TaskSessionRepository taskSessionRepository;
    private final PlayIntervalRepository playIntervalRepository;
    private final DailyElapsedTimeRepository dailyElapsedTimeRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public TaskSessionResponse startSession(Long userId, Long taskId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return taskSessionRepository
                .findFirstByTask_IdAndUser_IdAndStatusNotOrderByStartedAtDesc(taskId, userId, TaskSessionStatus.DONE)
                .map(TaskSessionResponse::from)
                .orElseGet(() -> {
                    TaskSession session = TaskSession.create(task, user, LocalDateTime.now(clock), null, 0, TaskSessionStatus.PAUSED);
                    taskSessionRepository.save(session);
                    return TaskSessionResponse.from(session);
                });
    }

    @Transactional(readOnly = true)
    public TaskSessionResponse getActiveSession(Long userId, Long taskId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
        return taskSessionRepository.findFirstByTask_IdAndUser_IdAndStatusNotOrderByStartedAtDesc(taskId, userId, TaskSessionStatus.DONE)
                .map(TaskSessionResponse::from)
                .orElse(null);
    }

    @Transactional
    public TaskSessionResponse updateSessionStatus(Long userId, Long sessionId, SessionStatusUpdateRequest request) {
        TaskSession session = taskSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (session.getStatus() == TaskSessionStatus.DONE) {
            throw new CustomException(ErrorCode.SESSION_ALREADY_DONE);
        }
        if (request.status() == TaskSessionStatus.PLAYING) {
            if (session.getStatus() == TaskSessionStatus.PLAYING
                    || taskSessionRepository.existsByUser_IdAndStatusAndTaskSessionIdNot(userId, TaskSessionStatus.PLAYING, sessionId)) {
                throw new CustomException(ErrorCode.SESSION_ALREADY_PLAYING);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);

        if (request.status() == TaskSessionStatus.PLAYING) {
            PlayInterval interval = PlayInterval.start(session, now);
            playIntervalRepository.save(interval);
        } else {
            // PAUSED or DONE: close open interval if exists
            playIntervalRepository.findByTaskSession_TaskSessionIdAndEndedAtIsNull(sessionId)
                    .ifPresent(interval -> interval.end(now));

            if (request.status() == TaskSessionStatus.DONE) {
                reflectDailyElapsedTime(session, now);
            }
        }

        session.updateElapsedTime(request.elapsedTime());
        session.updateStatus(request.status(), clock);
        return TaskSessionResponse.from(session);
    }

    private void reflectDailyElapsedTime(TaskSession session, LocalDateTime now) {
        List<PlayInterval> intervals = playIntervalRepository.findByTaskSession_TaskSessionId(session.getTaskSessionId());
        User user = session.getUser();

        intervals.stream()
                .filter(i -> i.getEndedAt() != null)
                .forEach(interval -> {
                    LocalDate date = interval.getStartedAt().toLocalDate();
                    LocalDate endDate = interval.getEndedAt().toLocalDate();
                    while (!date.isAfter(endDate)) {
                        int seconds = (int) interval.elapsedSecondsOn(date);
                        if (seconds > 0) {
                            LocalDate finalDate = date;
                            dailyElapsedTimeRepository.findByUser_IdAndDate(user.getId(), date)
                                    .ifPresentOrElse(
                                            record -> record.addElapsedSeconds(seconds),
                                            () -> dailyElapsedTimeRepository.save(DailyElapsedTime.create(user, finalDate, seconds))
                                    );
                        }
                        date = date.plusDays(1);
                    }
                });
    }
}

package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskSessionRepository extends JpaRepository<TaskSession, Long> {

    Optional<TaskSession> findFirstByTask_IdAndUser_IdAndStatusNotOrderByStartedAtDesc(Long taskId, Long userId, TaskSessionStatus status);

    @Query("select coalesce(sum(ts.elapsedTime), 0) from TaskSession ts where ts.task.id = :taskId and ts.user.id = :userId")
    long sumElapsedTimeByTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Query("select ts.task.id, coalesce(sum(ts.elapsedTime), 0) from TaskSession ts where ts.user.id = :userId and ts.task.id in :taskIds group by ts.task.id")
    List<Object[]> sumElapsedTimeByTaskIdsAndUserId(@Param("taskIds") Collection<Long> taskIds, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM TaskSession ts WHERE ts.task.id = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);

    boolean existsByTask_IdAndStatus(Long taskId, TaskSessionStatus status);

    @Query("select count(ts) > 0 from TaskSession ts where ts.task.sourceTask.id = :sourceTaskId and ts.task.deadline > :deadline and ts.status = :status")
    boolean existsBySourceTaskIdAndDeadlineAfterAndStatus(@Param("sourceTaskId") Long sourceTaskId, @Param("deadline") LocalDate deadline, @Param("status") TaskSessionStatus status);

    boolean existsByUser_IdAndStatusAndTaskSessionIdNot(Long userId, TaskSessionStatus status, Long sessionId);

    List<TaskSession> findByUser_IdAndStatusAndTaskSessionIdNot(Long userId, TaskSessionStatus status, Long taskSessionId);

    @Query("select coalesce(sum(ts.elapsedTime), 0) from TaskSession ts " +
           "where ts.user.id = :userId and ts.startedAt >= :startOfDay and ts.startedAt < :endOfDay")
    long sumElapsedTimeByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    @Query("select ts from TaskSession ts " +
           "where ts.user.id = :userId and ts.startedAt >= :startDateTime and ts.startedAt < :endDateTime")
    List<TaskSession> findByUserIdAndStartedAtBetween(
            @Param("userId") Long userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}

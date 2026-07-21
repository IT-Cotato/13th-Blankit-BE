package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskSessionRepository extends JpaRepository<TaskSession, Long> {

    Optional<TaskSession> findByTask_IdAndUser_IdAndStatusNot(Long taskId, Long userId, TaskSessionStatus status);

    @Query("select coalesce(sum(ts.elapsedTime), 0) from TaskSession ts where ts.task.id = :taskId and ts.user.id = :userId")
    long sumElapsedTimeByTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Query("select ts.task.id, coalesce(sum(ts.elapsedTime), 0) from TaskSession ts where ts.user.id = :userId and ts.task.id in :taskIds group by ts.task.id")
    List<Object[]> sumElapsedTimeByTaskIdsAndUserId(@Param("taskIds") Collection<Long> taskIds, @Param("userId") Long userId);

    void deleteByTaskId(Long taskId);
}

package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.PlayInterval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlayIntervalRepository extends JpaRepository<PlayInterval, Long> {

    List<PlayInterval> findByTaskSession_TaskSessionId(Long taskSessionId);

    Optional<PlayInterval> findByTaskSession_TaskSessionIdAndEndedAtIsNull(Long taskSessionId);

    @Modifying
    @Query("DELETE FROM PlayInterval p WHERE p.taskSession.task.id = :taskId")
    void deleteByTaskSession_Task_Id(@Param("taskId") Long taskId);
}

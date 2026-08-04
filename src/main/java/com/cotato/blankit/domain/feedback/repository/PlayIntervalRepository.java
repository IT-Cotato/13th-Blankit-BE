package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.PlayInterval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayIntervalRepository extends JpaRepository<PlayInterval, Long> {

    List<PlayInterval> findByTaskSession_TaskSessionId(Long taskSessionId);

    Optional<PlayInterval> findByTaskSession_TaskSessionIdAndEndedAtIsNull(Long taskSessionId);

    void deleteByTaskSession_Task_Id(Long taskId);
}

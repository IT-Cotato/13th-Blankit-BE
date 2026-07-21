package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.TaskSession;
import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskSessionRepository extends JpaRepository<TaskSession, Long> {

    Optional<TaskSession> findByTask_IdAndUser_IdAndStatusNot(Long taskId, Long userId, TaskSessionStatus status);
}

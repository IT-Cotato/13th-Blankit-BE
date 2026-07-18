package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.TaskSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskSessionRepository extends JpaRepository<TaskSession, Long> {
}

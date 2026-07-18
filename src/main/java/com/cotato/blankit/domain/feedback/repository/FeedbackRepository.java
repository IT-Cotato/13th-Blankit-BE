package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByTaskSessionAndIsDraftTrue(TaskSession taskSession);

    Optional<Feedback> findByTaskSessionAndIsDraftFalse(TaskSession taskSession);
}

package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByTaskSessionAndIsDraftTrue(TaskSession taskSession);

    Optional<Feedback> findByTaskSessionAndIsDraftFalse(TaskSession taskSession);

    List<Feedback> findByTask_IdAndIsDraftFalseOrderByCreatedAtAsc(Long taskId);

    void deleteByTask_Id(Long taskId);
}

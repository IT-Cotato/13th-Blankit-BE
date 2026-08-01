package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.feedback.entity.TaskSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByTaskSessionAndIsDraftTrue(TaskSession taskSession);

    Optional<Feedback> findByTaskSessionAndIsDraftFalse(TaskSession taskSession);

    List<Feedback> findByTask_IdAndIsDraftFalseOrderByCreatedAtAsc(Long taskId);

    void deleteByTask_Id(Long taskId);

    @Query("""
            select f from Feedback f
            join fetch f.task t
            join fetch t.category
            where f.user.id = :userId
              and f.isDraft = false
              and f.updatedAt >= :startOfDay
              and f.updatedAt < :endOfDay
            order by f.createdAt
            """)
    List<Feedback> findSubmittedByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );
}

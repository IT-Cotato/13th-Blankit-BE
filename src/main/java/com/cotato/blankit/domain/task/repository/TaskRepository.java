package com.cotato.blankit.domain.task.repository;

import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {"category", "similarTask", "sourceTask"})
    Optional<Task> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"category", "similarTask", "sourceTask"})
    @Query("""
            select distinct t
            from Task t
            where t.user.id = :userId
              and (:date is null or t.deadline = :date)
              and (:status is null or t.status = :status)
              and (:categoryId is null or t.category.id = :categoryId)
              and (:keyword is null or lower(t.title) like lower(concat('%', :keyword, '%')) escape '\\')
            order by t.deadline asc, t.createdAt asc, t.id asc
            """)
    List<Task> searchTaskCandidates(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("status") TaskStatus status,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword
    );

    @EntityGraph(attributePaths = {"category"})
    @Query("""
            select t
            from Task t
            where t.user.id = :userId
              and t.status = com.cotato.blankit.domain.task.entity.TaskStatus.DONE
              and (:categoryId is null or t.category.id = :categoryId)
              and (:keyword is null or lower(t.title) like lower(concat('%', :keyword, '%')) escape '\\')
            order by t.updatedAt desc, t.id desc
            """)
    Page<Task> searchHistory(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update Task t
            set t.similarTask = null
            where t.similarTask.id = :similarTaskId
              and t.user.id = :userId
            """)
    void clearSimilarTaskBySimilarTaskIdAndUserId(
            @Param("similarTaskId") Long similarTaskId,
            @Param("userId") Long userId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update Task t
            set t.sourceTask = null
            where t.sourceTask.id = :sourceTaskId
              and t.user.id = :userId
            """)
    void clearSourceTaskBySourceTaskIdAndUserId(
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("userId") Long userId
    );

    boolean existsBySourceTaskIdAndDeadline(Long sourceTaskId, LocalDate deadline);

    Optional<Task> findBySourceTaskIdAndDeadline(Long sourceTaskId, LocalDate deadline);

    boolean existsByCategoryIdAndUserId(Long categoryId, Long userId);
}

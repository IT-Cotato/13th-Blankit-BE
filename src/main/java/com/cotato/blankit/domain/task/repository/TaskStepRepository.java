package com.cotato.blankit.domain.task.repository;

import com.cotato.blankit.domain.task.entity.TaskStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskStepRepository extends JpaRepository<TaskStep, Long> {

    List<TaskStep> findByTaskIdOrderByTaskStepIdAsc(Long taskId);

    Optional<TaskStep> findByTaskStepIdAndTaskId(Long taskStepId, Long taskId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TaskStep ts where ts.task.id = :taskId")
    void deleteAllByTaskId(@Param("taskId") Long taskId);
}

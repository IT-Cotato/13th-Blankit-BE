package com.cotato.blankit.domain.task.repository;

import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.enums.TaskStatus;
import com.cotato.blankit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserAndStatusInAndIsDeletedFalseOrderByDeadlineAsc(User user, List<TaskStatus> statuses);

    Optional<Task> findByTaskIdAndUserAndIsDeletedFalse(Long taskId, User user);
}

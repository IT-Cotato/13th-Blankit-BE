package com.cotato.blankit.domain.task.entity;

import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "task",
        indexes = {
                @Index(name = "idx_task_user_deadline", columnList = "user_id,deadline"),
                @Index(name = "idx_task_user_status", columnList = "user_id,status"),
                @Index(name = "idx_task_user_category", columnList = "user_id,category_id"),
                @Index(name = "idx_task_similar_task", columnList = "similar_task_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "similar_task_id")
    private Task similarTask;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskPriority priority;

    @Column(name = "is_starred", nullable = false)
    private boolean starred;

    @Column(nullable = false)
    private LocalDate deadline;

    @Column(name = "estimated_time")
    private Integer estimatedTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    public static Task create(User user, Category category, String title, LocalDate deadline, Task similarTask) {
        return create(user, category, title, deadline, similarTask, null);
    }

    public static Task create(
            User user,
            Category category,
            String title,
            LocalDate deadline,
            Task similarTask,
            Integer estimatedTime
    ) {
        Task task = new Task();
        task.user = user;
        task.category = category;
        task.title = title;
        task.deadline = deadline;
        task.similarTask = similarTask;
        task.priority = null;
        task.starred = false;
        task.estimatedTime = estimatedTime;
        task.status = TaskStatus.TODO;
        return task;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public void updateCategory(Category category) {
        this.category = category;
    }

    public void updateStatus(TaskStatus status) {
        this.status = status;
    }

    public void updateStarred(boolean starred) {
        this.starred = starred;
    }

    public void updateSimilarTask(Task similarTask) {
        this.similarTask = similarTask;
    }

    public void clearSimilarTask() {
        this.similarTask = null;
    }
}

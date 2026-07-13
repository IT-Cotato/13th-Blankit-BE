package com.cotato.blankit.domain.task.entity;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.task.entity.enums.TaskPriority;
import com.cotato.blankit.domain.task.entity.enums.TaskStatus;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "similar_task_id")
    private Task similarTask;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('HIGH','MEDIUM','LOW')")
    private TaskPriority priority;

    @Column(nullable = false)
    private boolean isStarred;

    @Column(nullable = false)
    private LocalDate deadline;

    private Integer estimatedTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('TODO','IN_PROGRESS','DONE') DEFAULT 'TODO'")
    private TaskStatus status;

    @Column(nullable = false)
    private boolean isDeleted;
}

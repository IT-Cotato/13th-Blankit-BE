package com.cotato.blankit.domain.feedback.entity;

import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "task_session",
        indexes = {
                @Index(name = "idx_task_session_task", columnList = "task_id"),
                @Index(name = "idx_task_session_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Column(nullable = false)
    private int elapsedTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('PLAYING','PAUSED','DONE') DEFAULT 'PAUSED'")
    private TaskSessionStatus status;

    public static TaskSession create(
            Task task,
            User user,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            int elapsedTime,
            TaskSessionStatus status
    ) {
        TaskSession session = new TaskSession();
        session.task = task;
        session.user = user;
        session.startedAt = startedAt;
        session.endedAt = endedAt;
        session.elapsedTime = elapsedTime;
        session.status = status == null ? TaskSessionStatus.PAUSED : status;
        return session;
    }
}

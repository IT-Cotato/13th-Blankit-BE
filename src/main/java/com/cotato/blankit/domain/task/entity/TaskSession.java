package com.cotato.blankit.domain.task.entity;

import com.cotato.blankit.domain.user.entity.User;
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

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "task_session",
        indexes = {
                @Index(name = "idx_task_session_task", columnList = "task_id"),
                @Index(name = "idx_task_session_user", columnList = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "elapsed_time", nullable = false)
    private int elapsedTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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

package com.cotato.blankit.domain.feedback.entity;

import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long feedbackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_session_id", nullable = false)
    private TaskSession taskSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Integer progressRate;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(nullable = false)
    private boolean isCompleted;

    @Column(nullable = false)
    private boolean isDraft;

    private Integer intervalStartRate;

    private Integer cumulativeElapsedTime;

    private Integer consecutiveCount;

    private Integer intervalDiff;

    public static Feedback create(
            TaskSession taskSession,
            Task task,
            User user,
            Integer progressRate,
            String memo,
            boolean isDraft
    ) {
        Feedback feedback = new Feedback();
        feedback.taskSession = taskSession;
        feedback.task = task;
        feedback.user = user;
        feedback.progressRate = progressRate;
        feedback.memo = memo;
        feedback.isCompleted = false;
        feedback.isDraft = isDraft;
        return feedback;
    }

    public void update(Integer progressRate, String memo, boolean isDraft) {
        this.progressRate = progressRate;
        this.memo = memo;
        this.isDraft = isDraft;
    }

    public void updateMetrics(int intervalStartRate, int cumulativeElapsedTime, Integer consecutiveCount, Integer intervalDiff) {
        this.intervalStartRate = intervalStartRate;
        this.cumulativeElapsedTime = cumulativeElapsedTime;
        this.consecutiveCount = consecutiveCount;
        this.intervalDiff = intervalDiff;
    }

    public void complete() {
        this.isCompleted = true;
        this.isDraft = false;
    }
}

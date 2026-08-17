package com.cotato.blankit.domain.feedback.entity;

import com.cotato.blankit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "daily_elapsed_time",
        uniqueConstraints = @UniqueConstraint(columnNames = {"task_session_id", "date"}),
        indexes = @Index(name = "idx_daily_elapsed_session_date", columnList = "task_session_id, date")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyElapsedTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_session_id", nullable = false)
    private TaskSession taskSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int elapsedSeconds;

    public static DailyElapsedTime create(TaskSession taskSession, LocalDate date, int elapsedSeconds) {
        DailyElapsedTime record = new DailyElapsedTime();
        record.taskSession = taskSession;
        record.user = taskSession.getUser();
        record.date = date;
        record.elapsedSeconds = elapsedSeconds;
        return record;
    }

    public void setElapsedSeconds(int seconds) {
        this.elapsedSeconds = seconds;
    }
}

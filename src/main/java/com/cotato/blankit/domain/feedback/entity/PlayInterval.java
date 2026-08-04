package com.cotato.blankit.domain.feedback.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "play_interval",
        indexes = @Index(name = "idx_play_interval_session", columnList = "task_session_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayInterval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_session_id", nullable = false)
    private TaskSession taskSession;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    public static PlayInterval start(TaskSession taskSession, LocalDateTime startedAt) {
        PlayInterval interval = new PlayInterval();
        interval.taskSession = taskSession;
        interval.startedAt = startedAt;
        return interval;
    }

    public void end(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public long elapsedSecondsOn(LocalDate date) {
        if (endedAt == null) return 0;
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        LocalDateTime effectiveStart = startedAt.isBefore(startOfDay) ? startOfDay : startedAt;
        LocalDateTime effectiveEnd = endedAt.isAfter(endOfDay) ? endOfDay : endedAt;
        if (!effectiveEnd.isAfter(effectiveStart)) return 0;
        return ChronoUnit.SECONDS.between(effectiveStart, effectiveEnd);
    }
}

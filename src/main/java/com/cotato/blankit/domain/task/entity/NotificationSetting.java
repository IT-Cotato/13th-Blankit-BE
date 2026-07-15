package com.cotato.blankit.domain.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "notification_setting",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_setting_task", columnNames = "task_id"),
        indexes = @Index(name = "idx_notification_setting_task", columnList = "task_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_setting_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "notify_before", nullable = false)
    private Integer notifyBefore;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    public static NotificationSetting create(Task task, Integer notifyBefore, boolean enabled) {
        NotificationSetting setting = new NotificationSetting();
        setting.task = task;
        setting.notifyBefore = notifyBefore;
        setting.enabled = enabled;
        return setting;
    }

    public void update(Integer notifyBefore, boolean enabled) {
        this.notifyBefore = notifyBefore;
        this.enabled = enabled;
    }
}

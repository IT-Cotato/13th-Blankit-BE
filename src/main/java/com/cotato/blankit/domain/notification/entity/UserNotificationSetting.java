package com.cotato.blankit.domain.notification.entity;

import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_notification_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userNotificationSettingId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private boolean isServiceAlarmEnabled;

    // column name이 is_30min_pack_alarm_enabled 이므로 SpringPhysicalNamingStrategy 변환과 불일치 → 명시
    @Column(name = "is_30min_pack_alarm_enabled", nullable = false)
    private boolean thirtyMinPackAlarmEnabled;
}

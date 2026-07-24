package com.cotato.blankit.domain.notification.repository;

import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSetting, Long> {

    Optional<UserNotificationSetting> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    @Query("""
            select setting.user.id
            from UserNotificationSetting setting
            where setting.isServiceAlarmEnabled = true
            """)
    List<Long> findServiceNotificationRecipientUserIds();
}

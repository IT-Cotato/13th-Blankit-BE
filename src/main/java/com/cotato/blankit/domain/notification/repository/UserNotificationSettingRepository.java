package com.cotato.blankit.domain.notification.repository;

import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSetting, Long> {

    Optional<UserNotificationSetting> findByUserId(Long userId);

    @Modifying
    @Query("delete from UserNotificationSetting setting where setting.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    @Query("""
            select setting.user.id
            from UserNotificationSetting setting
            where setting.isServiceAlarmEnabled = true
            """)
    List<Long> findServiceNotificationRecipientUserIds();
}

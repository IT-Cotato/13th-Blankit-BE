package com.cotato.blankit.domain.push.service;

import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.push.entity.PushNotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {
    private final UserNotificationSettingRepository repository;

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId, PushNotificationType type) {
        return repository.findByUserId(userId)
                .map(setting -> enabled(setting, type))
                .orElse(false);
    }

    private boolean enabled(UserNotificationSetting setting, PushNotificationType type) {
        return switch (type) {
            case THIRTY_MIN_PACK -> setting.isThirtyMinPackAlarmEnabled();
            case SERVICE -> setting.isServiceAlarmEnabled();
        };
    }
}

package com.cotato.blankit.domain.notification.service;

import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationSettingRepository userNotificationSettingRepository;

    public void createDefaultSetting(User user) {
        userNotificationSettingRepository.save(UserNotificationSetting.createDefault(user));
    }
}

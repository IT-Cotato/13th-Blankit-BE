package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.entity.PushNotificationJob;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.notification.push.repository.PushNotificationJobRepository;

import java.util.Comparator;
import java.util.List;

public final class PushJobTestQueries {

    private PushJobTestQueries() {
    }

    public static List<PushNotificationJob> findByUserAndType(
            PushNotificationJobRepository repository,
            Long userId,
            PushNotificationType type
    ) {
        return repository.findAll().stream()
                .filter(job -> job.getUser().getId().equals(userId))
                .filter(job -> job.getType() == type)
                .sorted(Comparator.comparing(PushNotificationJob::getScheduledAt))
                .toList();
    }
}

package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.entity.PushSubscription;
import com.cotato.blankit.domain.user.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

final class PushSubscriptionFixture {

    private PushSubscriptionFixture() {
    }

    static PushSubscription create(User user, String fid) {
        PushSubscription subscription = BeanUtils.instantiateClass(PushSubscription.class);
        ReflectionTestUtils.setField(subscription, "user", user);
        ReflectionTestUtils.setField(subscription, "firebaseInstallationId", "installation-" + fid);
        ReflectionTestUtils.setField(subscription, "fcmToken", fid);
        ReflectionTestUtils.setField(subscription, "active", true);
        ReflectionTestUtils.setField(subscription, "lastRegisteredAt", LocalDateTime.now());
        ReflectionTestUtils.setField(subscription, "failureCount", 0);
        return subscription;
    }
}

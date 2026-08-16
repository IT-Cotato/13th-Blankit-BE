package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.dto.request.PushSubscriptionRequest;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.notification.push.repository.PushNotificationJobRepository;
import com.cotato.blankit.domain.notification.push.repository.PushSubscriptionRepository;
import com.cotato.blankit.domain.notification.push.service.PushNotificationJobService;
import com.cotato.blankit.domain.notification.push.service.PushSubscriptionService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PushUpsertConcurrencyTest {

    @Autowired PushSubscriptionService subscriptionService;
    @Autowired PushNotificationJobService jobService;
    @Autowired PushSubscriptionRepository subscriptionRepository;
    @Autowired PushNotificationJobRepository jobRepository;
    @Autowired UserRepository userRepository;

    @Test
    void concurrentSameFidRegistrationCreatesOneSubscription() throws Exception {
        User first = user("concurrent-first");
        User second = user("concurrent-second");
        String fid = "concurrent-fid-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(() -> registerAfterSignal(first.getId(), fid, ready, start));
            var secondResult = executor.submit(() -> registerAfterSignal(second.getId(), fid, ready, start));
            ready.await();
            start.countDown();

            Long firstId = firstResult.get();
            Long secondId = secondResult.get();

            assertThat(secondId).isEqualTo(firstId);
            long matchingSubscriptions = subscriptionRepository.findAll().stream()
                    .filter(subscription -> fid.equals(subscription.getFirebaseInstallationId()))
                    .count();
            assertThat(matchingSubscriptions).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameDedupeKeyCreatesOneJob() throws Exception {
        User user = user("concurrent-job");
        String dedupeKey = "concurrent-job-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> scheduleAfterSignal(user.getId(), dedupeKey, ready, start));
            var second = executor.submit(() -> scheduleAfterSignal(user.getId(), dedupeKey, ready, start));
            ready.await();
            start.countDown();

            assertThat(second.get()).isEqualTo(first.get());
            assertThat(jobRepository.findByDedupeKey(dedupeKey)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private Long registerAfterSignal(
            Long userId,
            String fid,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return subscriptionService.register(
                userId,
                new PushSubscriptionRequest(fid, "token-" + fid, "device", "browser")
        ).subscriptionId();
    }

    private Long scheduleAfterSignal(
            Long userId,
            String dedupeKey,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return jobService.schedule(
                userId,
                PushNotificationType.SERVICE,
                "NOTICE",
                "1",
                "title",
                "body",
                "/",
                LocalDateTime.now().plusHours(1),
                dedupeKey
        ).getId();
    }

    private User user(String prefix) {
        String unique = UUID.randomUUID().toString();
        return userRepository.save(User.create(
                SocialProvider.KAKAO,
                prefix + unique,
                prefix + unique + "@example.com",
                prefix,
                null,
                60
        ));
    }
}

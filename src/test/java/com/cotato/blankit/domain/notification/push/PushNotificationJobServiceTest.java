package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.notification.push.repository.PushNotificationJobRepository;
import com.cotato.blankit.domain.notification.push.service.PushNotificationJobService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PushNotificationJobServiceTest {
    @Autowired PushNotificationJobService service;
    @Autowired PushNotificationJobRepository repository;
    @Autowired UserRepository userRepository;

    @Test
    void sameDedupeKeyDoesNotCreateDuplicate() {
        User user = userRepository.save(User.create(SocialProvider.KAKAO, UUID.randomUUID().toString(),
                "job@example.com", "job", null, 60));
        LocalDateTime at = LocalDateTime.now().plusHours(1);
        long before = repository.count();
        var first = service.schedule(user.getId(), PushNotificationType.THIRTY_MIN_PACK,
                "TASK", "1", "title", "body", "/tasks/1", at, "key-1");
        var second = service.schedule(user.getId(), PushNotificationType.THIRTY_MIN_PACK,
                "TASK", "1", "title", "body", "/tasks/1", at, "key-1");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(repository.count()).isEqualTo(before + 1);
    }

    @Test
    void dueJobIsClaimedOnlyOnceAndTemporaryFailureIsRetried() {
        User user = userRepository.save(User.create(SocialProvider.KAKAO, UUID.randomUUID().toString(),
                "retry@example.com", "retry", null, 60));
        var job = service.schedule(user.getId(), PushNotificationType.SERVICE, "NOTICE", "2",
                "title", "body", "/", LocalDateTime.now().minusMinutes(1), "retry-key");

        var firstClaim = service.claimNext();
        var secondClaim = service.claimNext();
        assertThat(firstClaim).isPresent();
        assertThat(firstClaim.orElseThrow().id()).isEqualTo(job.getId());
        assertThat(secondClaim).isEmpty();

        service.complete(job.getId(), com.cotato.blankit.domain.notification.push.service.PushNotificationService
                .PushSendResult.retryAll(java.util.List.of("retry-fid")));
        var saved = repository.findById(job.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus.PENDING);
        assertThat(saved.getNextRetryAt()).isAfter(LocalDateTime.now());
        assertThat(saved.getRetryFids()).isNotBlank();
    }

    @Test
    void expiredProcessingLeaseIsClaimedAgain() {
        User user = userRepository.save(User.create(SocialProvider.KAKAO, UUID.randomUUID().toString(),
                "lease@example.com", "lease", null, 60));
        var job = service.schedule(user.getId(), PushNotificationType.SERVICE, "NOTICE", "3",
                "title", "body", "/", LocalDateTime.now().minusMinutes(10), "expired-lease-key");
        job.claim(LocalDateTime.now().minusMinutes(10));
        repository.saveAndFlush(job);

        var reclaimed = service.claimNext();

        assertThat(reclaimed).isPresent();
        assertThat(reclaimed.orElseThrow().id()).isEqualTo(job.getId());
        assertThat(reclaimed.orElseThrow().attempts()).isEqualTo(2);
    }
}

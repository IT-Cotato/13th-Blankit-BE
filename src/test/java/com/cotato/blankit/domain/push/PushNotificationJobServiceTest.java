package com.cotato.blankit.domain.push;

import com.cotato.blankit.domain.push.entity.PushNotificationType;
import com.cotato.blankit.domain.push.repository.PushNotificationJobRepository;
import com.cotato.blankit.domain.push.service.PushNotificationJobService;
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

        service.complete(job.getId(), com.cotato.blankit.domain.push.service.PushNotificationService
                .PushSendOutcome.RETRYABLE_FAILURE);
        var saved = repository.findById(job.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(com.cotato.blankit.domain.push.entity.PushNotificationJobStatus.PENDING);
        assertThat(saved.getNextRetryAt()).isAfter(LocalDateTime.now());
    }
}

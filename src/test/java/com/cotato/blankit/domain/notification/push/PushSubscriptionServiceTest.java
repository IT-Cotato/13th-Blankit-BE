package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.push.dto.request.PushSubscriptionRequest;
import com.cotato.blankit.domain.notification.push.entity.PushSubscription;
import com.cotato.blankit.domain.notification.push.repository.PushSubscriptionRepository;
import com.cotato.blankit.domain.notification.push.service.PushSubscriptionService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PushSubscriptionServiceTest {
    @Autowired PushSubscriptionService service;
    @Autowired PushSubscriptionRepository repository;
    @Autowired UserRepository userRepository;

    @Test
    void sameFidIsUpsertedAndReconnectedToCurrentUser() {
        User first = user("first");
        User second = user("second");
        var request = new PushSubscriptionRequest("fid-1", "token-1", "Mac", "Chrome");

        var initial = service.register(first.getId(), request);
        service.deactivate(first.getId(), initial.subscriptionId());
        var updated = service.register(second.getId(),
                new PushSubscriptionRequest("fid-1", "token-2", "PC", "Edge"));

        assertThat(updated.subscriptionId()).isEqualTo(initial.subscriptionId());
        assertThat(repository.count()).isEqualTo(1);
        PushSubscription saved = repository.findById(initial.subscriptionId()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(second.getId());
        assertThat(saved.getFirebaseInstallationId()).isEqualTo("fid-1");
        assertThat(saved.getFcmToken()).isEqualTo("token-2");
        assertThat(saved.getDeviceName()).isEqualTo("PC");
        assertThat(saved.getFailureCount()).isZero();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void tokenOwnedByAnotherInstallationDoesNotTransferSubscription() {
        User owner = user("token-owner");
        User requester = user("token-requester");
        var initial = service.register(owner.getId(),
                new PushSubscriptionRequest("owner-fid", "shared-token", "owner-device", "Chrome"));

        assertThatThrownBy(() -> service.register(requester.getId(),
                new PushSubscriptionRequest("requester-fid", "shared-token", "requester-device", "Edge")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(com.cotato.blankit.global.exception.ErrorCode.PUSH_SUBSCRIPTION_CONFLICT);

        assertThat(repository.count()).isEqualTo(1);
        PushSubscription saved = repository.findById(initial.subscriptionId()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(owner.getId());
        assertThat(saved.getFirebaseInstallationId()).isEqualTo("owner-fid");
        assertThat(saved.getFcmToken()).isEqualTo("shared-token");
    }

    @Test
    void simultaneousFidAndTokenConflictDoesNotChangeEitherSubscription() {
        User fidOwner = user("fid-owner");
        User tokenOwner = user("token-owner");
        var fidSubscription = service.register(fidOwner.getId(),
                new PushSubscriptionRequest("fid-a", "token-a", "device-a", "Chrome"));
        var tokenSubscription = service.register(tokenOwner.getId(),
                new PushSubscriptionRequest("fid-b", "token-b", "device-b", "Edge"));

        assertThatThrownBy(() -> service.register(fidOwner.getId(),
                new PushSubscriptionRequest("fid-a", "token-b", "changed-device", "Safari")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(com.cotato.blankit.global.exception.ErrorCode.PUSH_SUBSCRIPTION_CONFLICT);

        PushSubscription unchangedFid = repository.findById(fidSubscription.subscriptionId()).orElseThrow();
        PushSubscription unchangedToken = repository.findById(tokenSubscription.subscriptionId()).orElseThrow();
        assertThat(unchangedFid.getFcmToken()).isEqualTo("token-a");
        assertThat(unchangedFid.getDeviceName()).isEqualTo("device-a");
        assertThat(unchangedToken.getFirebaseInstallationId()).isEqualTo("fid-b");
        assertThat(unchangedToken.getFcmToken()).isEqualTo("token-b");
    }

    @Test
    void staleFailureDoesNotDeactivateSubscriptionAfterTokenRefresh() {
        User owner = user("refresh-owner");
        var initial = service.register(owner.getId(),
                new PushSubscriptionRequest("refresh-fid", "old-token", null, null));
        service.register(owner.getId(),
                new PushSubscriptionRequest("refresh-fid", "new-token", null, null));

        int affected = repository.deactivateAfterFailureIfTokenMatches(
                initial.subscriptionId(), "old-token", java.time.LocalDateTime.now());

        assertThat(affected).isZero();
        PushSubscription saved = repository.findById(initial.subscriptionId()).orElseThrow();
        assertThat(saved.getFcmToken()).isEqualTo("new-token");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getFailureCount()).isZero();
    }

    @Test
    void anotherUsersSubscriptionCannotBeDeactivated() {
        User owner = user("owner");
        User attacker = user("attacker");
        Long id = service.register(owner.getId(),
                new PushSubscriptionRequest("fid-owner", "token-owner", null, null)).subscriptionId();

        assertThatThrownBy(() -> service.deactivate(attacker.getId(), id))
                .isInstanceOf(CustomException.class);
        assertThat(repository.findById(id).orElseThrow().isActive()).isTrue();
    }

    private User user(String prefix) {
        return userRepository.save(User.create(SocialProvider.KAKAO, prefix + UUID.randomUUID(),
                prefix + "@example.com", prefix, null, 60));
    }
}

package com.cotato.blankit.domain.push;

import com.cotato.blankit.domain.push.dto.request.PushSubscriptionRequest;
import com.cotato.blankit.domain.push.entity.PushSubscription;
import com.cotato.blankit.domain.push.repository.PushSubscriptionRepository;
import com.cotato.blankit.domain.push.service.PushSubscriptionService;
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
        var request = new PushSubscriptionRequest("fid-1", "Mac", "Chrome");

        var initial = service.register(first.getId(), request);
        var updated = service.register(second.getId(), new PushSubscriptionRequest("fid-1", "PC", "Edge"));

        assertThat(updated.subscriptionId()).isEqualTo(initial.subscriptionId());
        assertThat(repository.count()).isEqualTo(1);
        PushSubscription saved = repository.findById(initial.subscriptionId()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(second.getId());
        assertThat(saved.getDeviceName()).isEqualTo("PC");
        assertThat(saved.getFailureCount()).isZero();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void anotherUsersSubscriptionCannotBeDeactivated() {
        User owner = user("owner");
        User attacker = user("attacker");
        Long id = service.register(owner.getId(),
                new PushSubscriptionRequest("fid-owner", null, null)).subscriptionId();

        assertThatThrownBy(() -> service.deactivate(attacker.getId(), id))
                .isInstanceOf(CustomException.class);
        assertThat(repository.findById(id).orElseThrow().isActive()).isTrue();
    }

    private User user(String prefix) {
        return userRepository.save(User.create(SocialProvider.KAKAO, prefix + UUID.randomUUID(),
                prefix + "@example.com", prefix, null, 60));
    }
}

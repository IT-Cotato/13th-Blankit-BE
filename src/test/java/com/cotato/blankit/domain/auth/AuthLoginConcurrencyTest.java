package com.cotato.blankit.domain.auth;

import com.cotato.blankit.domain.auth.dto.request.LoginRequest;
import com.cotato.blankit.domain.auth.service.AuthService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuthLoginConcurrencyTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;

    @Test
    void concurrentLoginFromDifferentInstallationsAllowsOnlyOneSession() throws Exception {
        String socialId = "concurrent-login-" + UUID.randomUUID();
        userRepository.save(User.create(
                SocialProvider.KAKAO,
                socialId,
                socialId + "@example.com",
                "concurrent-user",
                null,
                60
        ));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> loginAfterSignal(socialId, "device-a", ready, start));
            var second = executor.submit(() -> loginAfterSignal(socialId, "device-b", ready, start));
            ready.await();
            start.countDown();

            List<LoginAttempt> results = List.of(first.get(), second.get());
            assertThat(results).filteredOn(LoginAttempt::success).hasSize(1);
            assertThat(results)
                    .filteredOn(result -> !result.success())
                    .extracting(LoginAttempt::errorCode)
                    .containsExactly(ErrorCode.ANOTHER_DEVICE_ALREADY_LOGGED_IN);
        } finally {
            executor.shutdownNow();
        }
    }

    private LoginAttempt loginAfterSignal(
            String socialId,
            String installationId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            authService.login(new LoginRequest(
                    SocialProvider.KAKAO,
                    socialId,
                    "verified:KAKAO:" + socialId,
                    installationId
            ));
            return new LoginAttempt(true, null);
        } catch (CustomException exception) {
            return new LoginAttempt(false, exception.getErrorCode());
        }
    }

    private record LoginAttempt(boolean success, ErrorCode errorCode) {
    }
}

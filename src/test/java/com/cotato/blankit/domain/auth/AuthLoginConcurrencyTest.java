package com.cotato.blankit.domain.auth;

import com.cotato.blankit.domain.auth.dto.request.LoginRequest;
import com.cotato.blankit.domain.auth.dto.response.LoginResponse;
import com.cotato.blankit.domain.auth.repository.RefreshTokenRepository;
import com.cotato.blankit.domain.auth.service.AuthService;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.security.JwtTokenProvider;
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
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Test
    void concurrentLoginFromDifferentInstallationsLeavesOnlyLatestSessionActive() throws Exception {
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
            assertThat(results).allMatch(LoginAttempt::success);

            User user = userRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, socialId).orElseThrow();
            String activeSessionId = refreshTokenRepository.findByUserId(user.getId()).orElseThrow().getSessionId();
            assertThat(results.stream()
                    .map(LoginAttempt::accessToken)
                    .map(jwtTokenProvider::getSessionIdFromAccessToken)
                    .filter(activeSessionId::equals))
                    .hasSize(1);
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
        LoginResponse response = authService.login(new LoginRequest(
                    SocialProvider.KAKAO,
                    socialId,
                    "verified:KAKAO:" + socialId,
                    installationId
            ));
        return new LoginAttempt(true, response.accessToken());
    }

    private record LoginAttempt(boolean success, String accessToken) {
    }
}

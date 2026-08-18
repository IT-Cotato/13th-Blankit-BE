package com.cotato.blankit.support;

import com.cotato.blankit.domain.auth.entity.RefreshToken;
import com.cotato.blankit.domain.auth.repository.RefreshTokenRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccessTokenTestFactory {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String createAccessToken(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        String sessionId = UUID.randomUUID().toString();
        String installationId = "test-installation-" + UUID.randomUUID();
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        token -> token.rotate(
                                refreshToken,
                                jwtTokenProvider.getRefreshTokenExpiresAt(),
                                installationId,
                                sessionId
                        ),
                        () -> refreshTokenRepository.save(RefreshToken.create(
                                user,
                                refreshToken,
                                jwtTokenProvider.getRefreshTokenExpiresAt(),
                                installationId,
                                sessionId
                        ))
                );
        refreshTokenRepository.flush();
        return jwtTokenProvider.createAccessToken(userId, sessionId);
    }
}

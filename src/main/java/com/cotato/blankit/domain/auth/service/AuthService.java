package com.cotato.blankit.domain.auth.service;

import com.cotato.blankit.domain.auth.dto.request.RefreshTokenRequest;
import com.cotato.blankit.domain.auth.dto.request.LoginRequest;
import com.cotato.blankit.domain.auth.dto.request.SignupRequest;
import com.cotato.blankit.domain.auth.dto.response.TokenReissueResponse;
import com.cotato.blankit.domain.auth.dto.response.LoginResponse;
import com.cotato.blankit.domain.auth.dto.response.SignupResponse;
import com.cotato.blankit.domain.auth.dto.response.UserSummaryResponse;
import com.cotato.blankit.domain.auth.entity.RefreshToken;
import com.cotato.blankit.domain.auth.repository.RefreshTokenRepository;
import com.cotato.blankit.domain.auth.service.social.SocialTokenVerifier;
import com.cotato.blankit.domain.category.service.CategoryService;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import com.cotato.blankit.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SocialTokenVerifier socialTokenVerifier;
    private final CategoryService categoryService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        socialTokenVerifier.verify(request.socialProvider(), request.socialToken(), request.socialId());

        if (userRepository.existsBySocialProviderAndSocialId(request.socialProvider(), request.socialId())) {
            throw new CustomException(ErrorCode.DUPLICATE_SOCIAL_ACCOUNT);
        }

        User user = User.create(
                request.socialProvider(),
                request.socialId(),
                request.email(),
                request.nickname(),
                request.profileImageUrl(),
                request.recommendedDailyTime()
        );

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
            categoryService.createDefaultCategoriesIfNeverInitialized(savedUser);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_SOCIAL_ACCOUNT, e);
        }

        try {
            AuthTokens authTokens = issueAuthTokens(savedUser);
            return SignupResponse.of(authTokens.accessToken(), authTokens.refreshToken(), savedUser);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_CONFLICT, e);
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        socialTokenVerifier.verify(request.socialProvider(), request.socialToken(), request.socialId());

        User user = userRepository.findBySocialProviderAndSocialId(request.socialProvider(), request.socialId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        try {
            AuthTokens authTokens = issueAuthTokens(user);
            return LoginResponse.of(authTokens.accessToken(), authTokens.refreshToken(), UserSummaryResponse.from(user));
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_CONFLICT, e);
        }
    }

    @Transactional
    public TokenReissueResponse reissue(RefreshTokenRequest request) {
        Long userId = getUserIdFromRefreshToken(request.refreshToken());
        RefreshToken storedRefreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!storedRefreshToken.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        try {
            String newAccessToken = jwtTokenProvider.createAccessToken(user.getId());
            String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());
            storedRefreshToken.rotate(newRefreshToken, jwtTokenProvider.getRefreshTokenExpiresAt());
            refreshTokenRepository.flush();

            return TokenReissueResponse.of(newAccessToken, newRefreshToken);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN, e);
        }
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private AuthTokens issueAuthTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        saveOrRotateRefreshToken(user, refreshToken);
        return new AuthTokens(accessToken, refreshToken);
    }

    private void saveOrRotateRefreshToken(User user, String refreshToken) {
        refreshTokenRepository.findByUserId(user.getId())
                .ifPresentOrElse(
                        token -> token.rotate(refreshToken, jwtTokenProvider.getRefreshTokenExpiresAt()),
                        () -> refreshTokenRepository.save(RefreshToken.create(
                                user,
                                refreshToken,
                                jwtTokenProvider.getRefreshTokenExpiresAt()
                        ))
                );
        refreshTokenRepository.flush();
    }

    private Long getUserIdFromRefreshToken(String refreshToken) {
        try {
            return jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);
        } catch (CustomException e) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN, e);
        }
    }

    private record AuthTokens(String accessToken, String refreshToken) {
    }
}

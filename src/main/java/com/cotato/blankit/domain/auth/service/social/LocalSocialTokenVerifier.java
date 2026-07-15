package com.cotato.blankit.domain.auth.service.social;

import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class LocalSocialTokenVerifier implements SocialTokenVerifier {

    private static final String FLEXIBLE_TEST_TOKEN_PREFIX = "swagger-test:";
    private static final String KAKAO_FIXED_TEST_TOKEN = "swagger-test-kakao-token";
    private static final String KAKAO_FIXED_TEST_SOCIAL_ID = "swagger-kakao-user";
    private static final String GOOGLE_FIXED_TEST_TOKEN = "swagger-test-google-token";
    private static final String GOOGLE_FIXED_TEST_SOCIAL_ID = "swagger-google-user";

    @Override
    public void verify(SocialProvider socialProvider, String socialToken, String expectedSocialId) {
        // 테스트를 위한 코드: local 프로필 Swagger 수동 테스트에서만 유효하지 않은 소셜 토큰을 허용합니다.
        if (isFlexibleSwaggerTestToken(socialProvider, socialToken, expectedSocialId)
                || isFixedSwaggerTestToken(socialProvider, socialToken, expectedSocialId)) {
            return;
        }

        throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
    }

    private boolean isFlexibleSwaggerTestToken(SocialProvider socialProvider, String socialToken, String expectedSocialId) {
        String expectedToken = FLEXIBLE_TEST_TOKEN_PREFIX + socialProvider.name() + ":" + expectedSocialId;
        return expectedToken.equals(socialToken);
    }

    private boolean isFixedSwaggerTestToken(SocialProvider socialProvider, String socialToken, String expectedSocialId) {
        return switch (socialProvider) {
            case KAKAO -> KAKAO_FIXED_TEST_TOKEN.equals(socialToken)
                    && KAKAO_FIXED_TEST_SOCIAL_ID.equals(expectedSocialId);
            case GOOGLE -> GOOGLE_FIXED_TEST_TOKEN.equals(socialToken)
                    && GOOGLE_FIXED_TEST_SOCIAL_ID.equals(expectedSocialId);
        };
    }
}

package com.cotato.blankit.domain.auth;

import com.cotato.blankit.domain.auth.service.social.SocialTokenVerifier;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class TestSocialTokenVerifier implements SocialTokenVerifier {

    @Override
    public void verify(SocialProvider socialProvider, String socialToken, String expectedSocialId) {
        String expectedToken = "verified:" + socialProvider.name() + ":" + expectedSocialId;
        if (!expectedToken.equals(socialToken)) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
    }
}

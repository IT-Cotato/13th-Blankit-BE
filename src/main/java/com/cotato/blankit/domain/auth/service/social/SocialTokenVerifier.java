package com.cotato.blankit.domain.auth.service.social;

import com.cotato.blankit.domain.user.entity.SocialProvider;

public interface SocialTokenVerifier {

    void verify(SocialProvider socialProvider, String socialToken, String expectedSocialId);
}

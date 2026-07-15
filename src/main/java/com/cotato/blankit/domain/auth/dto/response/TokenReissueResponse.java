package com.cotato.blankit.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 재발급 응답")
public record TokenReissueResponse(
        @Schema(description = "JWT Access Token")
        String accessToken,

        @Schema(description = "JWT Refresh Token")
        String refreshToken,

        @Schema(description = "토큰 타입", example = "Bearer")
        String tokenType
) {

    public static TokenReissueResponse of(String accessToken, String refreshToken) {
        return new TokenReissueResponse(accessToken, refreshToken, "Bearer");
    }
}

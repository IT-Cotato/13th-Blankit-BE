package com.cotato.blankit.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 로그인 응답")
public record LoginResponse(
        @Schema(description = "JWT Access Token")
        String accessToken,

        @Schema(description = "JWT Refresh Token")
        String refreshToken,

        @Schema(description = "토큰 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "사용자 요약")
        UserSummaryResponse user
) {

    public static LoginResponse of(String accessToken, String refreshToken, UserSummaryResponse user) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", user);
    }
}

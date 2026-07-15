package com.cotato.blankit.domain.auth.dto.response;

import com.cotato.blankit.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 회원가입 응답")
public record SignupResponse(
        @Schema(description = "JWT Access Token")
        String accessToken,

        @Schema(description = "JWT Refresh Token")
        String refreshToken,

        @Schema(description = "토큰 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "소셜 제공자", example = "KAKAO")
        String socialProvider,

        @Schema(description = "이메일", example = "user@example.com")
        String email,

        @Schema(description = "닉네임", example = "서윤")
        String nickname,

        @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.png")
        String profileImageUrl,

        @Schema(description = "ERD user.recommended_daily_time 값", example = "120")
        Integer recommendedDailyTime
) {

    public static SignupResponse of(String accessToken, String refreshToken, User user) {
        return new SignupResponse(
                accessToken,
                refreshToken,
                "Bearer",
                user.getId(),
                user.getSocialProvider().name(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getRecommendedDailyTime()
        );
    }
}

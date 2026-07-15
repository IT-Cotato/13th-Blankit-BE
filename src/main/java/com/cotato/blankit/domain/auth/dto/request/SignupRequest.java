package com.cotato.blankit.domain.auth.dto.request;

import com.cotato.blankit.domain.user.entity.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "소셜 회원가입 요청")
public record SignupRequest(
        @Schema(description = "소셜 제공자", example = "KAKAO", allowableValues = {"KAKAO", "GOOGLE"})
        @NotNull(message = "소셜 제공자는 필수입니다.")
        SocialProvider socialProvider,

        @Schema(description = "소셜 제공자의 사용자 식별자", example = "swagger-kakao-user")
        @NotBlank(message = "소셜 ID는 필수입니다.")
        String socialId,

        @Schema(description = "소셜 제공자에서 발급받은 Access Token 또는 ID Token. local 프로필에서는 Swagger 테스트용 토큰을 사용할 수 있습니다.", example = "swagger-test-kakao-token")
        @NotBlank(message = "소셜 토큰은 필수입니다.")
        String socialToken,

        @Schema(description = "이메일", example = "user@example.com")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "닉네임", example = "블랭킷")
        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname,

        @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.png")
        String profileImageUrl,

        @Schema(description = "일일 추천 시간(분). 0~1440 사이 값을 허용합니다.", example = "120", minimum = "0", maximum = "1440")
        @Min(value = 0, message = "추천 일일 시간은 0 이상이어야 합니다.")
        @Max(value = 1440, message = "추천 일일 시간은 1440분(24시간) 이하여야 합니다.")
        Integer recommendedDailyTime
) {
}

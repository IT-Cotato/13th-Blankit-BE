package com.cotato.blankit.domain.auth.dto.request;

import com.cotato.blankit.domain.user.entity.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "소셜 로그인 요청")
public record LoginRequest(
        @Schema(description = "소셜 제공자", example = "KAKAO", allowableValues = {"KAKAO", "GOOGLE"})
        @NotNull(message = "소셜 제공자는 필수입니다.")
        SocialProvider socialProvider,

        @Schema(description = "소셜 제공자의 사용자 식별자", example = "swagger-kakao-user")
        @NotBlank(message = "소셜 ID는 필수입니다.")
        String socialId,

        @Schema(description = "소셜 제공자에서 발급받은 Access Token 또는 ID Token. local 프로필에서는 Swagger 테스트용 토큰을 사용할 수 있습니다.", example = "swagger-test-kakao-token")
        @NotBlank(message = "소셜 토큰은 필수입니다.")
        String socialToken
) {
}

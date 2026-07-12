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

        @Schema(description = "소셜 제공자의 사용자 식별자", example = "123456789")
        @NotBlank(message = "소셜 ID는 필수입니다.")
        String socialId
) {
}

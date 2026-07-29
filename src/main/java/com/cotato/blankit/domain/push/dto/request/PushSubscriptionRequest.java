package com.cotato.blankit.domain.push.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @Schema(description = "Firebase Installation ID", example = "cV7...")
        @NotBlank @Size(max = 255) String installationId,
        @Schema(example = "MacBook Air") @Size(max = 100) String deviceName,
        @Schema(example = "Chrome") @Size(max = 100) String browser
) {
}

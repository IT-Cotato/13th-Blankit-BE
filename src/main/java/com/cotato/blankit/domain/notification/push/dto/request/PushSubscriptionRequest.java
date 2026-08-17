package com.cotato.blankit.domain.notification.push.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @Schema(description = "Firebase Installation ID", example = "cV7...")
        @NotBlank @Size(max = 255) String installationId,
        @Schema(description = "Firebase Cloud Messaging registration token", example = "fcm-registration-token")
        @NotBlank @Size(max = 512) String fcmToken,
        @Schema(example = "MacBook Air") @Size(max = 100) String deviceName,
        @Schema(example = "Chrome") @Size(max = 100) String browser
) {
}

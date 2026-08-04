package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "세부 단계 생성 요청")
public record TaskStepCreateRequest(

        @Schema(description = "단계 제목", example = "개념 정리")
        @NotBlank(message = "단계 제목은 필수입니다.")
        @Size(max = 100, message = "단계 제목은 100자 이하여야 합니다.")
        String title
) {
}

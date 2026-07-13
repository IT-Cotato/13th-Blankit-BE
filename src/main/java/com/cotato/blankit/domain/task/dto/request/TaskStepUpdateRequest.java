package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "세부 단계 수정 요청")
public record TaskStepUpdateRequest(

        @Schema(description = "단계 제목", example = "심화 문제 풀이")
        @Size(max = 100, message = "단계 제목은 100자 이하여야 합니다.")
        String title,

        @Schema(description = "진척도 (0~100)", example = "50")
        @Min(value = 0, message = "진척도는 0 이상이어야 합니다.")
        @Max(value = 100, message = "진척도는 100 이하여야 합니다.")
        Integer progressRate
) {
}

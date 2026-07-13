package com.cotato.blankit.domain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세부 단계 응답")
public record TaskStepResponse(

        @Schema(description = "세부 단계 ID", example = "1")
        Long taskStepId,

        @Schema(description = "단계 제목", example = "개념 정리")
        String title,

        @Schema(description = "진척도 (0~100)", example = "100")
        int progressRate,

        @Schema(description = "정렬 순서", example = "0")
        int sortOrder
) {
}

package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "세부 단계 일괄 생성 요청 (기본 3개: 개념 정리·문제 풀이·전체 복습하기)")
public record TaskStepBulkCreateRequest(

        @Schema(description = "세부 단계 목록")
        @NotEmpty(message = "세부 단계는 최소 1개 이상이어야 합니다.")
        List<StepItem> steps
) {

    @Schema(description = "단계 항목")
    public record StepItem(

            @Schema(description = "단계 제목", example = "개념 정리")
            @NotBlank(message = "단계 제목은 필수입니다.")
            @Size(max = 100, message = "단계 제목은 100자 이하여야 합니다.")
            String title,

            @Schema(description = "정렬 순서", example = "0")
            int sortOrder
    ) {
    }
}

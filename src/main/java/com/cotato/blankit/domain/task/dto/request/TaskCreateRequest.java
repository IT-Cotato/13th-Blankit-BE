package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "과업 추가 요청")
public record TaskCreateRequest(

        @Schema(description = "과업명", example = "기말고사 준비")
        @NotBlank(message = "과업명은 필수입니다.")
        @Size(max = 255, message = "과업명은 255자 이하여야 합니다.")
        String title,

        @Schema(description = "카테고리 ID", example = "1")
        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @Schema(description = "마감일", example = "2026-07-25")
        @NotNull(message = "마감일은 필수입니다.")
        LocalDate deadline,

        @Schema(description = "비슷한 과거 과업 ID (예상 시간 보정용, 선택)", example = "5")
        Long similarTaskId,

        @Schema(description = "알림 시점 (마감일 기준 분 전, 기본 1440=1일 전)", example = "1440")
        @Min(value = 10, message = "알림 시점은 최소 10분 이상이어야 합니다.")
        Integer notifyBefore,

        @Schema(description = "반복 설정 (선택)")
        @Valid
        RepeatRuleRequest repeatRule
) {
}

package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "과업 수정 요청")
public record TaskUpdateRequest(

        @Schema(description = "과업명", example = "기말고사 준비 (심화)")
        @Size(max = 255, message = "과업명은 255자 이하여야 합니다.")
        String title,

        @Schema(description = "카테고리 ID", example = "2")
        Long categoryId,

        @Schema(description = "마감일", example = "2026-07-28")
        LocalDate deadline,

        @Schema(description = "비슷한 과거 과업 ID (선택)", example = "5")
        Long similarTaskId,

        @Schema(description = "알림 시점 (분 전)", example = "720")
        Integer notifyBefore,

        @Schema(description = "반복 설정 (null = 반복 제거)")
        @Valid
        RepeatRuleRequest repeatRule
) {
}

package com.cotato.blankit.domain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "완료 과업 응답 (마이페이지 4.3 조회 및 비슷한 과업 선택 2.16에서 공통 사용)")
public record TaskCompletedResponse(

        @Schema(description = "과업 ID", example = "5")
        Long taskId,

        @Schema(description = "과업명", example = "중간고사 수학 공부")
        String title,

        @Schema(description = "카테고리 ID (2.16 비슷한 과업 선택 시 프론트가 이 값으로 그룹핑)", example = "1")
        Long categoryId,

        @Schema(description = "카테고리명", example = "학업")
        String categoryName,

        @Schema(description = "카테고리 색상 (HEX)", example = "#FF5C5C")
        String categoryColor,

        @Schema(description = "카테고리 아이콘 식별 키", example = "book")
        String categoryIconKey,

        @Schema(description = "완료 일자 (마감일 기준)", example = "2026-06-20")
        LocalDate deadline,

        @Schema(description = "총 소요 시간 (초)", example = "10800")
        int totalElapsedSeconds
) {
}

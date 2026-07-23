package com.cotato.blankit.domain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "캘린더 날짜별 과업 응답")
public record TaskCalendarResponse(

        @Schema(description = "조회 날짜", example = "2026-07-15")
        LocalDate date,

        @Schema(description = "해당 날짜에 마감일을 가진 과업 목록")
        List<TaskCalendarItem> tasks
) {

    @Schema(description = "캘린더 과업 항목 (동그라미 렌더링용)")
    public record TaskCalendarItem(

            @Schema(description = "과업 ID", example = "1")
            Long taskId,

            @Schema(description = "과업명", example = "기말고사 준비")
            String title,

            @Schema(description = "카테고리 색상 (HEX, 캘린더 동그라미 색상)", example = "#FF5C5C")
            String categoryColor,

            @Schema(description = "카테고리 아이콘 식별 키", example = "book")
            String categoryIconKey,

            @Schema(description = "과업 상태", example = "TODO")
            String status
    ) {
    }
}

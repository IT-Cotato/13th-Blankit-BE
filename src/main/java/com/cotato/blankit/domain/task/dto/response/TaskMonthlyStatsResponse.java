package com.cotato.blankit.domain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "월별 통계 응답 (캘린더 색상 렌더링용, 3.4.1)")
public record TaskMonthlyStatsResponse(

        @Schema(description = "조회 연도", example = "2026")
        int year,

        @Schema(description = "조회 월 (1~12)", example = "7")
        int month,

        @Schema(description = "일별 통계 목록")
        List<DayStatsItem> dailyStats
) {

    @Schema(description = "일별 통계 항목")
    public record DayStatsItem(

            @Schema(description = "날짜", example = "2026-07-13")
            LocalDate date,

            @Schema(description = "실제 소요 시간 (분, 과거·오늘만 제공, 미래 날짜는 null)", example = "90")
            Integer actualMinutes,

            @Schema(description = "권장 시간 (분, 과거·오늘·미래 모두 제공)", example = "120")
            int recommendedMinutes
    ) {
    }
}

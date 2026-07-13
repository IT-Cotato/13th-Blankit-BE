package com.cotato.blankit.domain.task.controller;

import com.cotato.blankit.domain.task.dto.response.TaskDailyStatsResponse;
import com.cotato.blankit.domain.task.dto.response.TaskMonthlyStatsResponse;
import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import com.cotato.blankit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@NotImplementedYet
@Tag(name = "과업 - 통계", description = "캘린더 통계 화면(3.4~3.6) API")
@RestController
@RequestMapping("/api/v1/tasks/stats")
public class TaskStatsController {

    @Operation(summary = "일별 통계 조회",
            description = "특정 날짜의 실제 소요 시간, 권장 시간, 피드백 완료 과업 목록을 반환합니다. " +
                    "과거·오늘: totalElapsedSeconds 제공 / 미래: totalElapsedSeconds = null. " +
                    "권장 시간은 logic-spec 5번 공식(과업별 남은 예상 시간 ÷ 마감일까지 남은 일수 합산)으로 계산합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "날짜 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/daily")
    public ApiResponse<TaskDailyStatsResponse> getDailyStats(
            @Parameter(description = "조회 날짜 (YYYY-MM-DD)", example = "2026-07-13", required = true)
            @RequestParam LocalDate date) {
        return ApiResponse.success(new TaskDailyStatsResponse(
                date,
                5400,
                120,
                List.of(
                        new TaskDailyStatsResponse.FeedbackTaskItem(
                                1L, "기말고사 준비", "학업", "#FF5C5C", 40, false),
                        new TaskDailyStatsResponse.FeedbackTaskItem(
                                2L, "영어 단어 100개 암기", "학업", "#FF5C5C", 100, true)
                )
        ));
    }

    @Operation(summary = "월별 통계 조회",
            description = "해당 월의 일별 실제 소요 시간·권장 시간을 반환합니다. " +
                    "캘린더 색상 렌더링(3.4.1)에 사용됩니다. " +
                    "과거·오늘: actualMinutes 제공 / 미래 날짜: actualMinutes = null.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "파라미터 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/monthly")
    public ApiResponse<TaskMonthlyStatsResponse> getMonthlyStats(
            @Parameter(description = "조회 연도", example = "2026", required = true)
            @RequestParam int year,
            @Parameter(description = "조회 월 (1~12)", example = "7", required = true)
            @RequestParam int month) {
        return ApiResponse.success(new TaskMonthlyStatsResponse(year, month, List.of(
                new TaskMonthlyStatsResponse.DayStatsItem(LocalDate.of(year, month, 11), 90, 120),
                new TaskMonthlyStatsResponse.DayStatsItem(LocalDate.of(year, month, 12), 150, 120),
                new TaskMonthlyStatsResponse.DayStatsItem(LocalDate.of(year, month, 13), 90, 120),
                new TaskMonthlyStatsResponse.DayStatsItem(LocalDate.of(year, month, 14), null, 120),
                new TaskMonthlyStatsResponse.DayStatsItem(LocalDate.of(year, month, 15), null, 120)
        )));
    }
}

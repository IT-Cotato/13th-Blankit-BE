package com.cotato.blankit.domain.task.controller;

import com.cotato.blankit.domain.task.dto.response.TaskDailyStatsResponse;
import com.cotato.blankit.domain.task.dto.response.TaskMonthlyStatsResponse;
import com.cotato.blankit.domain.task.service.TaskStatsService;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "과업 - 통계", description = "캘린더 통계 화면(3.4~3.6) API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks/stats")
public class TaskStatsController {

    private final TaskStatsService taskStatsService;

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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회 날짜 (YYYY-MM-DD)", example = "2026-07-13", required = true)
            @RequestParam LocalDate date) {
        return ApiResponse.success(taskStatsService.getDailyStats(userDetails.getUserId(), date));
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회 연도", example = "2026", required = true)
            @RequestParam int year,
            @Parameter(description = "조회 월 (1~12)", example = "7", required = true)
            @RequestParam int month) {
        return ApiResponse.success(taskStatsService.getMonthlyStats(userDetails.getUserId(), year, month));
    }
}

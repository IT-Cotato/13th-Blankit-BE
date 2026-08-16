package com.cotato.blankit.domain.recommendation.controller;

import com.cotato.blankit.domain.recommendation.dto.response.AllRecommendationResponse;
import com.cotato.blankit.domain.recommendation.dto.response.RecommendationModesResponse;
import com.cotato.blankit.domain.recommendation.dto.response.TodayRecommendationResponse;
import com.cotato.blankit.domain.recommendation.dto.response.ThirtyMinutePackRecommendationResponse;
import com.cotato.blankit.domain.recommendation.service.RecommendationService;
import com.cotato.blankit.domain.task.entity.TaskPriority;
import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Tag(name = "추천", description = "우선순위 과업 추천 및 과업 조합 추천 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(summary = "오늘의 추천 조회",
            description = "오늘의 권장 시간(logic-spec 5번)과 우선순위 상위 3개 과업(logic-spec 1번)을 반환합니다. " +
                    "홈 화면 '오늘의 권장 시간' 및 '우선순위 과목 추천' 영역에 사용됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/today")
    public ApiResponse<TodayRecommendationResponse> getTodayRecommendation(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(recommendationService.getTodayRecommendation(userDetails.getUserId()));
    }

    @Operation(summary = "우선순위 전체 과업 조회",
            description = "우선순위 점수 기준으로 정렬된 전체 활성 과업을 반환합니다. '우선순위 과목 추천 전체 보기' 화면에 사용됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/all")
    public ApiResponse<AllRecommendationResponse> getAllRecommendation(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(recommendationService.getAllRecommendation(userDetails.getUserId()));
    }

    @Operation(summary = "30분 Pack 과업 추천",
            description = "시간표 사이 30분 공백 동안 분당 진행률을 가장 빠르게 높일 수 있는 활성 과업 최대 3개를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추천 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "공백 시간 범위 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/pack30")
    public ApiResponse<ThirtyMinutePackRecommendationResponse> getThirtyMinutePackRecommendation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam @Min(30) @Max(30) int availableMinutes
    ) {
        return ApiResponse.success(
                recommendationService.getThirtyMinutePackRecommendation(userDetails.getUserId(), availableMinutes));
    }

    @Operation(summary = "과업 조합 추천 목록 조회",
            description = "FIRE(불끄기)·BALANCE(밸런스)·TASTE(찍먹)·CLEAR(해치우기) 모드별 과업 조합을 반환합니다. " +
                    "홈 화면 '과업 조합 추천' 영역에 사용됩니다. 각 모드의 추천 로직은 logic-spec 4번 참고.\n\n" +
                    "**[갱신 시점]** 결과는 당일 첫 호출 시 계산되어 하루 동안 고정됩니다. " +
                    "자정이 지나면 다음 첫 호출 시 최신 과업 상태를 기반으로 재계산됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/modes")
    public ApiResponse<RecommendationModesResponse> getRecommendationModes(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(recommendationService.getRecommendationModes(userDetails.getUserId()));
    }
}

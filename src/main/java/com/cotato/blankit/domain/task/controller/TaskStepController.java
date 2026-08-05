package com.cotato.blankit.domain.task.controller;

import com.cotato.blankit.domain.task.dto.request.TaskStepCreateRequest;
import com.cotato.blankit.domain.task.dto.request.TaskStepUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.TaskStepResponse;
import com.cotato.blankit.domain.task.service.TaskStepService;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "과업 - 세부 단계", description = "과업 세부 단계(TaskStep) 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks/{taskId}/steps")
public class TaskStepController {

    private final TaskStepService taskStepService;

    @Operation(summary = "세부 단계 목록 조회",
            description = "과업에 등록된 세부 단계를 생성 순서대로 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @GetMapping
    public ApiResponse<List<TaskStepResponse>> getSteps(
            @PathVariable Long taskId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(taskStepService.getSteps(taskId, userDetails.getUserId()));
    }

    @Operation(summary = "세부 단계 생성",
            description = "세부 단계를 하나 추가합니다. 첫 단계 추가 시 과업 진행률이 0으로 초기화됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskStepResponse> createStep(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskStepCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(taskStepService.createStep(taskId, userDetails.getUserId(), request));
    }

    @Operation(summary = "세부 단계 수정",
            description = "세부 단계 제목 또는 진척도를 수정합니다. null 필드는 변경하지 않습니다. " +
                    "진척도 변경 시 전체 과업 진행률이 자동 재계산됩니다(logic-spec 2번 공식).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "단계 없음")
    })
    @PatchMapping("/{stepId}")
    public ApiResponse<TaskStepResponse> updateStep(
            @PathVariable Long taskId,
            @PathVariable Long stepId,
            @RequestBody @Valid TaskStepUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(taskStepService.updateStep(taskId, stepId, userDetails.getUserId(), request));
    }

    @Operation(summary = "세부 단계 삭제",
            description = "세부 단계를 삭제합니다. 삭제 후 전체 과업 진행률이 자동 재계산됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "단계 없음")
    })
    @DeleteMapping("/{stepId}")
    public ApiResponse<Void> deleteStep(
            @PathVariable Long taskId,
            @PathVariable Long stepId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        taskStepService.deleteStep(taskId, stepId, userDetails.getUserId());
        return ApiResponse.success();
    }
}

package com.cotato.blankit.domain.task.controller;

import com.cotato.blankit.domain.task.dto.request.TaskStepBulkCreateRequest;
import com.cotato.blankit.domain.task.dto.request.TaskStepUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.TaskStepResponse;
import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import com.cotato.blankit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@NotImplementedYet
@Tag(name = "과업 - 세부 단계", description = "과업 세부 단계(TaskStep) 관리 API")
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/steps")
public class TaskStepController {

    @Operation(summary = "세부 단계 목록 조회",
            description = "과업에 등록된 세부 단계를 sort_order 오름차순으로 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @GetMapping
    public ApiResponse<List<TaskStepResponse>> getSteps(@PathVariable Long taskId) {
        return ApiResponse.success(List.of(
                new TaskStepResponse(1L, "개념 정리", 100, 0),
                new TaskStepResponse(2L, "문제 풀이", 20, 1),
                new TaskStepResponse(3L, "전체 복습하기", 0, 2)
        ));
    }

    @Operation(summary = "세부 단계 일괄 생성",
            description = "세부 단계를 일괄 생성합니다. " +
                    "피드백 화면에서 단계 쪼개기 버튼 클릭 시 기본 3개(개념 정리·문제 풀이·전체 복습하기)가 전달됩니다. " +
                    "기존 단계가 있으면 모두 교체됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<TaskStepResponse>> createSteps(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskStepBulkCreateRequest request) {
        List<TaskStepResponse> steps = new java.util.ArrayList<>();
        for (int i = 0; i < request.steps().size(); i++) {
            steps.add(new TaskStepResponse((long) (i + 1), request.steps().get(i).title(), 0, i));
        }
        return ApiResponse.success(steps);
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
            @RequestBody @Valid TaskStepUpdateRequest request) {
        return ApiResponse.success(new TaskStepResponse(
                stepId,
                request.title() != null ? request.title() : "개념 정리",
                request.progressRate() != null ? request.progressRate() : 0,
                0
        ));
    }

    @Operation(summary = "세부 단계 삭제",
            description = "세부 단계를 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "단계 없음")
    })
    @DeleteMapping("/{stepId}")
    public ApiResponse<Void> deleteStep(
            @PathVariable Long taskId,
            @PathVariable Long stepId) {
        return ApiResponse.success();
    }
}

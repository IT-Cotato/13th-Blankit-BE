package com.cotato.blankit.domain.task.controller;

import com.cotato.blankit.domain.task.dto.request.TaskCreateRequest;
import com.cotato.blankit.domain.task.dto.request.TaskStarUpdateRequest;
import com.cotato.blankit.domain.task.dto.request.TaskUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.TaskCalendarResponse;
import com.cotato.blankit.domain.task.dto.response.TaskDetailResponse;
import com.cotato.blankit.domain.task.dto.response.TaskFormOptionsResponse;
import com.cotato.blankit.domain.task.dto.response.TaskListResponse;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.service.TaskService;
import com.cotato.blankit.domain.task.service.TaskStatsService;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.response.PageResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Task", description = "홈 화면 과업 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tasks")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;
    private final TaskStatsService taskStatsService;

    @Operation(
            summary = "과업 등록 화면 초기값 조회",
            description = "기본 카테고리, 기본 알림 1440분, 반복 없음(defaultRepeatEnabled=false), 활성 카테고리 목록과 알림 범위를 반환합니다."
    )
    @GetMapping("/form-options")
    public ApiResponse<TaskFormOptionsResponse> getFormOptions(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(taskService.getFormOptions(userDetails.getUserId()));
    }

    @Operation(
            summary = "과업 생성",
            description = "마감일과 하나 이상의 챕터를 함께 등록합니다. 챕터는 전달 순서대로 저장됩니다. 일반 과업은 deadline이 필수이며, 반복 과업은 repeatRule 조건으로 서버가 가장 가까운 deadline을 계산합니다. notifyBefore 생략 시 1440, notificationEnabled 생략 시 true입니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "일반 과업",
                                            value = """
                                                    {
                                                      "title": "알고리즘 과제 제출",
                                                      "deadline": "2026-08-12",
                                                      "notifyBefore": 1440,
                                                      "notificationEnabled": true,
                                                      "categoryId": 1,
                                                      "chapters": ["1장 자료구조", "2장 알고리즘"]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "매주 반복 과업",
                                            value = """
                                                    {
                                                      "title": "주간 회의",
                                                      "notifyBefore": 1440,
                                                      "notificationEnabled": true,
                                                      "categoryId": 1,
                                                      "repeatRule": {
                                                        "frequency": "WEEKLY",
                                                        "daysOfWeek": [1, 3, 5],
                                                        "startDate": "2026-08-12",
                                                        "endDate": "2026-12-31"
                                                      },
                                                      "chapters": ["1주차 개념", "1주차 문제 풀이"]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    )
    @PostMapping
    public ResponseEntity<ApiResponse<TaskDetailResponse>> createTask(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TaskCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskService.createTask(userDetails.getUserId(), request)));
    }

    @Operation(
            summary = "캘린더 월별 과업 조회",
            description = "해당 월에 마감일이 있는 과업을 날짜별로 반환합니다. 동그라미(●) 렌더링용 (functional-spec 3.2)."
    )
    @GetMapping("/calendar")
    public ApiResponse<List<TaskCalendarResponse>> getCalendar(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회 연도", example = "2026", required = true)
            @Min(1970) @Max(9999) @RequestParam int year,
            @Parameter(description = "조회 월 (1~12)", example = "7", required = true)
            @Min(1) @Max(12) @RequestParam int month
    ) {
        return ApiResponse.success(taskStatsService.getMonthlyCalendar(userDetails.getUserId(), year, month));
    }

    @Operation(
            summary = "과업 목록 조회",
            description = "홈 화면 과업 목록을 조회합니다. date는 KST 기준 LocalDate로 해석하며 실제 저장된 과업의 deadline이 조회 날짜와 같은 항목만 반환합니다. 반복 과업은 직전 회차 마감일에 스케줄러가 sourceTaskId가 있는 다음 회차 과업으로 미리 생성합니다."
    )
    @GetMapping
    public ApiResponse<PageResponse<TaskListResponse>> getTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회 날짜", example = "2026-08-12")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @Parameter(description = "과업 상태", schema = @Schema(allowableValues = {"TODO", "IN_PROGRESS", "DONE"}))
            @RequestParam(required = false)
            TaskStatus status,
            @Parameter(description = "카테고리 ID", example = "1")
            @RequestParam(required = false)
            Long categoryId,
            @Parameter(description = "과업명 검색어", example = "알고리즘")
            @RequestParam(required = false)
            String keyword,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0")
            int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20")
            int size
    ) {
        return ApiResponse.success(taskService.getTasks(
                userDetails.getUserId(),
                date,
                status,
                categoryId,
                keyword,
                page,
                size
        ));
    }

    @Operation(summary = "과업 상세 조회", description = "인증된 사용자 본인의 과업 상세 정보를 조회합니다.")
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailResponse> getTask(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "과업 ID", example = "1")
            @PathVariable Long taskId
    ) {
        return ApiResponse.success(taskService.getTask(userDetails.getUserId(), taskId));
    }

    @Operation(
            summary = "과업 수정",
            description = "전달된 필드만 수정합니다. repeatRule 필드가 전달되면 반복 설정 전체를 교체하고 deadline도 다시 계산합니다. clearRepeatRule=true이면 repeat_rule을 삭제하며 단일 deadline을 함께 전달해야 합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "일반 수정",
                                            value = """
                                                    {
                                                      "title": "알고리즘 과제 최종 제출",
                                                      "deadline": "2026-08-12",
                                                      "notifyBefore": 1440,
                                                      "notificationEnabled": true,
                                                      "categoryId": 1,
                                                      "status": "TODO",
                                                      "starred": false
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "매주 반복으로 수정",
                                            value = """
                                                    {
                                                      "title": "알고리즘 과제 최종 제출",
                                                      "notifyBefore": 1440,
                                                      "notificationEnabled": true,
                                                      "repeatRule": {
                                                        "frequency": "WEEKLY",
                                                        "daysOfWeek": [1, 3, 5],
                                                        "startDate": "2026-08-12",
                                                        "endDate": "2026-12-31"
                                                      },
                                                      "categoryId": 1,
                                                      "status": "TODO",
                                                      "starred": false
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    )
    @PatchMapping("/{taskId}")
    public ApiResponse<TaskDetailResponse> updateTask(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "과업 ID", example = "1")
            @PathVariable Long taskId,
            @Valid @RequestBody TaskUpdateRequest request
    ) {
        return ApiResponse.success(taskService.updateTask(userDetails.getUserId(), taskId, request));
    }

    @Operation(summary = "과업 별표 설정/해제", description = "과업의 중요 표시(starred) 상태를 설정하거나 해제합니다. 원하는 최종 상태를 전송하면 멱등하게 반영됩니다.")
    @PatchMapping("/{taskId}/star")
    public ApiResponse<TaskDetailResponse> updateStarred(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "과업 ID", example = "1")
            @PathVariable Long taskId,
            @Valid @RequestBody TaskStarUpdateRequest request
    ) {
        return ApiResponse.success(taskService.updateStarred(userDetails.getUserId(), taskId, request));
    }

    @Operation(summary = "과업 삭제", description = "ERD에 task.is_deleted가 없어 현재는 hard delete합니다. 반복/알림은 삭제하고, 참조 중인 다른 과업은 similarTask 연결만 해제합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재생 중인 과업은 삭제할 수 없음 (TASK_SESSION_ACTIVE)")
    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> deleteTask(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "과업 ID", example = "1")
            @PathVariable Long taskId
    ) {
        taskService.deleteTask(userDetails.getUserId(), taskId);
        return ApiResponse.success();
    }
}

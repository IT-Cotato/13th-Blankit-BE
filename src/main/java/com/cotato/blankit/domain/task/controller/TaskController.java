package com.cotato.blankit.domain.task.controller;

import com.cotato.blankit.domain.task.dto.request.TaskCreateRequest;
import com.cotato.blankit.domain.task.dto.request.TaskUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.TaskDetailResponse;
import com.cotato.blankit.domain.task.dto.response.TaskFormOptionsResponse;
import com.cotato.blankit.domain.task.dto.response.TaskHistoryResponse;
import com.cotato.blankit.domain.task.dto.response.TaskListResponse;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.service.TaskService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@Tag(name = "Task", description = "홈 화면 과업 및 이전 완료 과업 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tasks")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;

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
            description = "일반 과업은 deadline이 필수이며 사용자가 선택한 날짜를 저장합니다. 반복 과업은 repeatRule.startDate와 반복 조건으로 서버가 가장 가까운 deadline을 계산하며, endDate는 생략할 수 있습니다. notifyBefore 생략 시 1440, notificationEnabled 생략 시 true입니다. notifyBefore는 10, 60, 1440, 4320, 10080만 허용합니다. repeatRule이 없으면 repeat_rule 레코드를 만들지 않습니다. similarTaskId는 nullable입니다. similarTaskId가 없으면 직접 입력한 estimatedTime(분)을 저장하고, 있으면 유사 과업의 총 소요시간을 estimatedTime으로 반영합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "일반 과업 - 유사 과업 없음",
                                            value = """
                                                    {
                                                      "title": "알고리즘 과제 제출",
                                                      "deadline": "2026-08-12",
                                                      "notifyBefore": 1440,
                                                      "notificationEnabled": true,
                                                      "categoryId": 1,
                                                      "estimatedTime": 90,
                                                      "similarTaskId": null
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
                                                      "estimatedTime": 60,
                                                      "similarTaskId": null
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
            summary = "과업 목록 조회",
            description = "홈 화면 과업 목록을 조회합니다. date는 KST 기준 LocalDate로 해석하며 실제 저장된 과업의 deadline이 조회 날짜와 같은 항목만 반환합니다. 반복 과업은 스케줄러가 발생일에 sourceTaskId가 있는 새 과업으로 생성합니다."
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
            description = "전달된 필드만 수정합니다. similarTask 연결 해제는 clearSimilarTask=true로 요청합니다."
                    + " repeatRule 필드가 전달되면 반복 설정 전체를 교체하고 deadline도 다시 계산합니다. clearRepeatRule=true이면 repeat_rule을 삭제하며 단일 deadline을 함께 전달해야 합니다.",
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
                                    ),
                                    @ExampleObject(
                                            name = "유사 과업 연결 해제",
                                            value = """
                                                    {
                                                      "clearSimilarTask": true
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

    @Operation(summary = "과업 삭제", description = "ERD에 task.is_deleted가 없어 현재는 hard delete합니다. 반복/알림은 삭제하고, 참조 중인 다른 과업은 similarTask 연결만 해제합니다.")
    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> deleteTask(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "과업 ID", example = "1")
            @PathVariable Long taskId
    ) {
        taskService.deleteTask(userDetails.getUserId(), taskId);
        return ApiResponse.success();
    }

    @Operation(
            summary = "이전 완료 과업 검색",
            description = "비슷한 과업 선택 화면에서 사용합니다. 현재 로그인한 사용자의 DONE 과업만 조회하며, totalElapsedTime은 task_session.elapsed_time 합계입니다. 카드 완료 일자는 deadline입니다."
    )
    @GetMapping("/history")
    public ApiResponse<PageResponse<TaskHistoryResponse>> getHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "과업명 검색어", example = "알고리즘")
            @RequestParam(required = false)
            String keyword,
            @Parameter(description = "카테고리 ID", example = "1")
            @RequestParam(required = false)
            Long categoryId,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0")
            int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20")
            int size
    ) {
        return ApiResponse.success(taskService.getHistory(
                userDetails.getUserId(),
                keyword,
                categoryId,
                page,
                size
        ));
    }
}

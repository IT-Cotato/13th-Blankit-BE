package com.cotato.blankit.domain.task.controller;

import com.cotato.blankit.domain.task.dto.request.*;
import com.cotato.blankit.domain.task.dto.response.*;
import com.cotato.blankit.domain.task.entity.enums.RepeatFrequency;
import com.cotato.blankit.domain.task.entity.enums.TaskPriority;
import com.cotato.blankit.domain.task.entity.enums.TaskStatus;
import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import com.cotato.blankit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@NotImplementedYet
@Tag(name = "과업", description = "과업(Task) CRUD 및 상태 관리 API")
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    /* ── 진행 중 과업 목록 ── */

    @Operation(summary = "진행 중 과업 목록 조회",
            description = "로그인 사용자의 TODO·IN_PROGRESS 과업을 우선순위 점수 오름차순으로 반환합니다. " +
                    "홈 화면 과업 카드 및 중요 과업 선택 화면(4.4)에 사용됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<List<TaskListResponse>> getTasks() {
        return ApiResponse.success(List.of(
                new TaskListResponse(1L, "기말고사 준비", 1L, "학업", "#FF5C5C",
                        TaskPriority.HIGH, true, LocalDate.of(2026, 7, 20), 180,
                        TaskStatus.IN_PROGRESS, 40, "2단원까지 정리 완료"),
                new TaskListResponse(2L, "영어 단어 100개 암기", 1L, "학업", "#FF5C5C",
                        TaskPriority.MEDIUM, false, LocalDate.of(2026, 7, 25), 60,
                        TaskStatus.TODO, 20, null),
                new TaskListResponse(3L, "운동 계획 세우기", 2L, "일상", "#5C9EFF",
                        TaskPriority.LOW, false, LocalDate.of(2026, 7, 30), 30,
                        TaskStatus.TODO, 0, null)
        ));
    }

    /* ── 과업 추가 ── */

    @Operation(summary = "과업 추가",
            description = "새 과업을 추가합니다. notifyBefore(알림), repeatRule(반복), similarTaskId(비슷한 과업)는 선택 항목입니다. " +
                    "notifyBefore 미입력 시 기본값 1440(1일 전)이 적용됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "카테고리 없음")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskDetailResponse> createTask(@RequestBody @Valid TaskCreateRequest request) {
        RepeatRuleResponse repeatRuleResponse = request.repeatRule() != null
                ? new RepeatRuleResponse(1L, RepeatFrequency.WEEKLY, "1,3,5", null, null,
                LocalDate.of(2026, 7, 14), LocalDate.of(2026, 12, 31))
                : null;
        NotificationSettingResponse notiResponse =
                new NotificationSettingResponse(1L, request.notifyBefore() != null ? request.notifyBefore() : 1440, true);

        return ApiResponse.success(new TaskDetailResponse(
                10L, request.title(), request.categoryId(), "학업", "#FF5C5C",
                request.similarTaskId(), null, false, request.deadline(),
                null, TaskStatus.TODO, 0, null,
                List.of(), repeatRuleResponse, notiResponse
        ));
    }

    /* ── 과업 상세 ── */

    @Operation(summary = "과업 상세 조회",
            description = "과업 상세 정보를 조회합니다. 세부 단계, 반복 설정, 알림 설정을 포함합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailResponse> getTask(@PathVariable Long taskId) {
        return ApiResponse.success(new TaskDetailResponse(
                taskId, "기말고사 준비", 1L, "학업", "#FF5C5C", 5L,
                TaskPriority.HIGH, true, LocalDate.of(2026, 7, 20), 180,
                TaskStatus.IN_PROGRESS, 40, "2단원까지 정리 완료",
                List.of(
                        new TaskStepResponse(1L, "개념 정리", 100, 0),
                        new TaskStepResponse(2L, "문제 풀이", 20, 1),
                        new TaskStepResponse(3L, "전체 복습하기", 0, 2)
                ),
                new RepeatRuleResponse(1L, RepeatFrequency.WEEKLY, "1,3,5", null, null,
                        LocalDate.of(2026, 7, 14), LocalDate.of(2026, 12, 31)),
                new NotificationSettingResponse(1L, 1440, true)
        ));
    }

    /* ── 과업 수정 ── */

    @Operation(summary = "과업 수정",
            description = "과업 정보를 수정합니다. null 필드는 변경하지 않습니다. " +
                    "repeatRule을 null로 전달하면 기존 반복 설정이 삭제됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @PatchMapping("/{taskId}")
    public ApiResponse<TaskDetailResponse> updateTask(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskUpdateRequest request) {
        return ApiResponse.success(new TaskDetailResponse(
                taskId, request.title() != null ? request.title() : "기말고사 준비",
                1L, "학업", "#FF5C5C", request.similarTaskId(),
                TaskPriority.HIGH, false,
                request.deadline() != null ? request.deadline() : LocalDate.of(2026, 7, 25),
                180, TaskStatus.TODO, 40, null,
                List.of(), null,
                new NotificationSettingResponse(1L, request.notifyBefore() != null ? request.notifyBefore() : 1440, true)
        ));
    }

    /* ── 과업 삭제 ── */

    @Operation(summary = "과업 삭제",
            description = "과업을 소프트 삭제합니다. 연결된 task_step, repeat_rule, notification_setting도 함께 삭제됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> deleteTask(@PathVariable Long taskId) {
        return ApiResponse.success();
    }

    /* ── 중요 표시 ── */

    @Operation(summary = "중요 표시 설정",
            description = "중요 표시(별) 상태를 설정합니다. 원하는 최종 상태를 전달하므로 멱등합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @PatchMapping("/{taskId}/star")
    public ApiResponse<Void> updateStar(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskStarUpdateRequest request) {
        return ApiResponse.success();
    }

    /* ── 상태 변경 ── */

    @Operation(summary = "과업 상태 변경",
            description = "과업 상태를 변경합니다. DONE으로 변경 시 홈 화면에서 제거되고 완료 과업 목록으로 이동합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @PatchMapping("/{taskId}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskStatusUpdateRequest request) {
        return ApiResponse.success();
    }

    /* ── 완료 과업 목록 ── */

    @Operation(summary = "완료 과업 목록 조회",
            description = "DONE 상태 과업을 최신 마감일 순으로 반환합니다. " +
                    "마이페이지 내 완료 과업 보기(4.3)와 비슷한 과업 선택(2.16) 모두 동일 응답을 사용합니다. " +
                    "2.16에서 카테고리별 그룹핑이 필요한 경우 응답의 categoryId 기준으로 프론트가 처리합니다. " +
                    "keyword 파라미터로 과업명 검색, 최대 3년 이전 과업까지 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/completed")
    public ApiResponse<List<TaskCompletedResponse>> getCompletedTasks(
            @Parameter(description = "과업명 검색어 (선택)", example = "수학")
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "deadline", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(List.of(
                new TaskCompletedResponse(5L, "중간고사 수학 공부", 1L, "학업", "#FF5C5C",
                        LocalDate.of(2026, 6, 20), 10800),
                new TaskCompletedResponse(6L, "영어 에세이 작성", 1L, "학업", "#FF5C5C",
                        LocalDate.of(2026, 6, 15), 7200),
                new TaskCompletedResponse(7L, "헬스장 등록", 2L, "일상", "#5C9EFF",
                        LocalDate.of(2026, 6, 10), 1800)
        ));
    }

    /* ── 중요 과업 목록 ── */

    @Operation(summary = "중요 표시 과업 목록 조회",
            description = "is_starred = true이고 마감일이 지나지 않은 과업을 우선순위 상·중·하로 구분하여 반환합니다. " +
                    "마이페이지 중요한 과업 선택하기 화면(4.4)에 사용됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/starred")
    public ApiResponse<List<TaskListResponse>> getStarredTasks() {
        return ApiResponse.success(List.of(
                new TaskListResponse(1L, "기말고사 준비", 1L, "학업", "#FF5C5C",
                        TaskPriority.HIGH, true, LocalDate.of(2026, 7, 20), 180,
                        TaskStatus.IN_PROGRESS, 40, "2단원까지 정리 완료"),
                new TaskListResponse(4L, "자격증 시험 접수", 4L, "자격증 준비", "#FFB85C",
                        TaskPriority.MEDIUM, true, LocalDate.of(2026, 7, 31), 60,
                        TaskStatus.TODO, 0, null)
        ));
    }

    /* ── 캘린더용 날짜별 과업 ── */

    @Operation(summary = "날짜별 과업 조회 (캘린더)",
            description = "특정 날짜를 마감일로 갖는 과업 목록을 반환합니다. " +
                    "캘린더 동그라미 렌더링(3.2) 및 날짜 클릭 시 과업 목록 표시(3.1.2)에 사용됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "날짜 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/calendar")
    public ApiResponse<TaskCalendarResponse> getCalendarTasks(
            @Parameter(description = "조회 날짜 (YYYY-MM-DD)", example = "2026-07-20", required = true)
            @RequestParam LocalDate date) {
        return ApiResponse.success(new TaskCalendarResponse(
                date,
                List.of(
                        new TaskCalendarResponse.TaskCalendarItem(1L, "기말고사 준비", "#FF5C5C", "IN_PROGRESS"),
                        new TaskCalendarResponse.TaskCalendarItem(8L, "팀 프로젝트 발표", "#FF5C5C", "TODO")
                )
        ));
    }

    /* ── 반복 설정 ── */

    @Operation(summary = "반복 설정 추가",
            description = "과업에 반복 규칙을 추가합니다. 과업당 1개만 허용됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류 또는 이미 존재"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @PostMapping("/{taskId}/repeat-rule")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RepeatRuleResponse> createRepeatRule(
            @PathVariable Long taskId,
            @RequestBody @Valid RepeatRuleRequest request) {
        return ApiResponse.success(new RepeatRuleResponse(
                1L, request.frequency(), request.daysOfWeek(),
                request.daysOfMonth(), request.monthOfYear(),
                request.startDate(), request.endDate()
        ));
    }

    @Operation(summary = "반복 설정 수정",
            description = "과업의 반복 규칙을 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 또는 반복 설정 없음")
    })
    @PatchMapping("/{taskId}/repeat-rule")
    public ApiResponse<RepeatRuleResponse> updateRepeatRule(
            @PathVariable Long taskId,
            @RequestBody @Valid RepeatRuleRequest request) {
        return ApiResponse.success(new RepeatRuleResponse(
                1L, request.frequency(), request.daysOfWeek(),
                request.daysOfMonth(), request.monthOfYear(),
                request.startDate(), request.endDate()
        ));
    }

    @Operation(summary = "반복 설정 삭제",
            description = "과업의 반복 규칙을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 또는 반복 설정 없음")
    })
    @DeleteMapping("/{taskId}/repeat-rule")
    public ApiResponse<Void> deleteRepeatRule(@PathVariable Long taskId) {
        return ApiResponse.success();
    }

    /* ── 알림 설정 ── */

    @Operation(summary = "과업 알림 수정",
            description = "과업에 설정된 알림 시점 및 활성화 여부를 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @PatchMapping("/{taskId}/notification")
    public ApiResponse<NotificationSettingResponse> updateNotification(
            @PathVariable Long taskId,
            @RequestBody @Valid NotificationSettingRequest request) {
        return ApiResponse.success(new NotificationSettingResponse(1L, request.notifyBefore(), request.isEnabled()));
    }

    @Operation(summary = "과업 알림 삭제",
            description = "과업에 설정된 알림을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @DeleteMapping("/{taskId}/notification")
    public ApiResponse<Void> deleteNotification(@PathVariable Long taskId) {
        return ApiResponse.success();
    }
}

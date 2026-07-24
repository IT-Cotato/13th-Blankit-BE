package com.cotato.blankit.domain.feedback.controller;

import com.cotato.blankit.domain.feedback.dto.request.FeedbackSubmitRequest;
import com.cotato.blankit.domain.feedback.dto.request.SessionStatusUpdateRequest;
import com.cotato.blankit.domain.feedback.dto.response.FeedbackResponse;
import com.cotato.blankit.domain.feedback.dto.response.TaskSessionResponse;
import com.cotato.blankit.domain.feedback.service.FeedbackService;
import com.cotato.blankit.domain.feedback.service.TaskSessionService;
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

@Tag(name = "피드백 & 세션", description = "과업 플레이 세션 및 피드백 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class FeedbackController {

    private final TaskSessionService taskSessionService;
    private final FeedbackService feedbackService;

    @Operation(summary = "세션 시작",
            description = "과업 플레이 세션을 시작합니다. 최초 진입 시 상태는 PAUSED이며, 사용자가 재생 버튼을 눌러야 PLAYING으로 변경됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "세션 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @PostMapping("/tasks/{taskId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskSessionResponse> startSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long taskId) {
        return ApiResponse.success(taskSessionService.startSession(userDetails.getUserId(), taskId));
    }

    @Operation(summary = "세션 상태 변경",
            description = "세션 상태를 변경합니다. " +
                    "재생 버튼 클릭 → PLAYING, 일시정지 버튼 클릭 → PAUSED, 완료(체크) 버튼 클릭 → DONE. " +
                    "DONE으로 변경 시 endedAt이 기록됩니다. " +
                    "elapsedTime은 클라이언트가 관리하는 누적 소요 시간(초)으로, 상태 변경 시마다 최신 값을 반드시 함께 전송해야 합니다. " +
                    "서버는 이 값을 저장해두었다가 세션 재진입 시 반환하므로 타이머를 이어서 시작할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션 없음")
    })
    @PatchMapping("/sessions/{sessionId}/status")
    public ApiResponse<TaskSessionResponse> updateSessionStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @RequestBody @Valid SessionStatusUpdateRequest request) {
        return ApiResponse.success(taskSessionService.updateSessionStatus(userDetails.getUserId(), sessionId, request));
    }

    @Operation(summary = "활성 세션 조회",
            description = "과업의 진행 중(PLAYING) 또는 일시정지(PAUSED) 세션을 반환합니다. " +
                    "앱 재시작 시 플레이 화면 진입 전 호출하여 이전 세션을 이어받을 수 있습니다. " +
                    "활성 세션이 없으면 data=null을 반환하며, 이 경우 새 세션을 생성해야 합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공 (활성 세션 없으면 data=null)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @GetMapping("/tasks/{taskId}/sessions/active")
    public ApiResponse<TaskSessionResponse> getActiveSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long taskId) {
        return ApiResponse.success(taskSessionService.getActiveSession(userDetails.getUserId(), taskId));
    }

    @Operation(summary = "피드백 조회",
            description = "세션에 연결된 피드백을 조회합니다. " +
                    "임시저장(isDraft=true) 피드백이 있으면 해당 내용을 반환하여 이어서 작성할 수 있습니다. " +
                    "피드백이 없으면 null을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공 (피드백 없으면 data=null)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션 없음")
    })
    @GetMapping("/sessions/{sessionId}/feedback")
    public ApiResponse<FeedbackResponse> getFeedback(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId) {
        return ApiResponse.success(feedbackService.getFeedback(userDetails.getUserId(), sessionId));
    }

    @Operation(summary = "피드백 제출",
            description = "피드백을 저장합니다. isDraft=true 시 임시저장, false 시 최종 제출입니다. " +
                    "progressRate와 memo 중 최소 하나는 입력해야 완료 버튼이 활성화됩니다(프론트 처리). " +
                    "세부 단계가 있는 경우 progressRate는 무시되며, 단계별 진척도 평균이 적용됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션 없음")
    })
    @PostMapping("/sessions/{sessionId}/feedback")
    public ApiResponse<FeedbackResponse> submitFeedback(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @RequestBody @Valid FeedbackSubmitRequest request) {
        return ApiResponse.success(feedbackService.submitFeedback(userDetails.getUserId(), sessionId, request));
    }
}

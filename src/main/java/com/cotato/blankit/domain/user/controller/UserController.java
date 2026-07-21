package com.cotato.blankit.domain.user.controller;

import com.cotato.blankit.domain.user.dto.response.UserMeResponse;
import com.cotato.blankit.domain.user.service.UserService;
import com.cotato.blankit.domain.user.dto.request.TimetableSettingsUpdateRequest;
import com.cotato.blankit.domain.user.dto.request.UserNotificationSettingUpdateRequest;
import com.cotato.blankit.domain.user.dto.response.TimetableSettingsResponse;
import com.cotato.blankit.domain.user.dto.response.UserNotificationSettingResponse;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


@Tag(name = "사용자", description = "사용자 프로필 및 설정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 조회",
            description = "현재 JWT 인증 사용자 정보를 조회합니다. onboardingCompleted 필드는 ERD에 없어 응답하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(userService.getMe(userDetails.getUserId()));
    }

    @Operation(
            summary = "회원탈퇴",
            description = "현재 인증 사용자를 물리 삭제합니다. ERD에 탈퇴 상태 컬럼이 없어 소프트 삭제는 적용하지 않습니다. Stateless JWT 구조이므로 기존 Access Token은 만료 전까지 서버 저장소에서 별도 폐기하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.withdraw(userDetails.getUserId());
        return ApiResponse.success();
    }

    @Operation(summary = "시간표 표시 범위 수정",
            description = "홈 화면·시간표 화면에서 표시할 시작/종료 시간을 수정합니다. 기본값: 08:00 ~ 00:00.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/timetable-settings")
    public ApiResponse<TimetableSettingsResponse> updateTimetableSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid TimetableSettingsUpdateRequest request) {
        return ApiResponse.success(userService.updateTimetableSettings(userDetails.getUserId(), request));
    }

    @Operation(summary = "사용자 알림 설정 조회",
            description = "서비스 알림과 30분 Pack 알림의 활성화 여부를 조회합니다. 최초 가입 시 모두 OFF.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/me/notification-settings")
    public ApiResponse<UserNotificationSettingResponse> getNotificationSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(new UserNotificationSettingResponse(false, false));
    }

    @Operation(summary = "사용자 알림 설정 수정",
            description = "서비스 알림과 30분 Pack 알림 수신 여부를 설정합니다. " +
                    "기기 알림 권한이 없는 상태에서 ON 요청 시 권한 안내 필요(앱에서 처리).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/notification-settings")
    public ApiResponse<UserNotificationSettingResponse> updateNotificationSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid UserNotificationSettingUpdateRequest request) {
        return ApiResponse.success(new UserNotificationSettingResponse(
                request.isServiceAlarmEnabled(), request.is30minPackAlarmEnabled()
        ));
    }
}

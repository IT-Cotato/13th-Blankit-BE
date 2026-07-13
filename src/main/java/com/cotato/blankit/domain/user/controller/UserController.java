package com.cotato.blankit.domain.user.controller;

import com.cotato.blankit.domain.user.dto.request.TimetableSettingsUpdateRequest;
import com.cotato.blankit.domain.user.dto.request.UserNotificationSettingUpdateRequest;
import com.cotato.blankit.domain.user.dto.response.TimetableSettingsResponse;
import com.cotato.blankit.domain.user.dto.response.UserNotificationSettingResponse;
import com.cotato.blankit.domain.user.dto.response.UserProfileResponse;
import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import com.cotato.blankit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;

@NotImplementedYet
@Tag(name = "사용자", description = "사용자 프로필 및 설정 API")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Operation(summary = "내 프로필 조회",
            description = "로그인한 사용자의 프로필 정보와 시간표 시간 범위를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile() {
        return ApiResponse.success(new UserProfileResponse(
                1L, "옥준승", "junseung0305@gmail.com",
                "https://example.com/profile/1.jpg",
                LocalTime.of(8, 0), LocalTime.of(0, 0)
        ));
    }

    @Operation(summary = "시간표 표시 범위 수정",
            description = "홈 화면·시간표 화면에서 표시할 시작/종료 시간을 수정합니다. 기본값: 08:00 ~ 24:00.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/timetable-settings")
    public ApiResponse<TimetableSettingsResponse> updateTimetableSettings(
            @RequestBody @Valid TimetableSettingsUpdateRequest request) {
        return ApiResponse.success(new TimetableSettingsResponse(request.startTime(), request.endTime()));
    }

    @Operation(summary = "사용자 알림 설정 조회",
            description = "서비스 알림과 30분 Pack 알림의 활성화 여부를 조회합니다. 최초 가입 시 모두 OFF.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/me/notification-settings")
    public ApiResponse<UserNotificationSettingResponse> getNotificationSettings() {
        return ApiResponse.success(new UserNotificationSettingResponse(false, false));
    }

    @Operation(summary = "사용자 알림 설정 수정",
            description = "서비스 알림과 30분 Pack 알림 수신 여부를 설정합니다. " +
                    "기기 알림 권한이 없는 상태에서 ON 요청 시 권한 안내 필요(앱에서 처리).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/me/notification-settings")
    public ApiResponse<UserNotificationSettingResponse> updateNotificationSettings(
            @RequestBody @Valid UserNotificationSettingUpdateRequest request) {
        return ApiResponse.success(new UserNotificationSettingResponse(
                request.isServiceAlarmEnabled(), request.is30minPackAlarmEnabled()
        ));
    }
}

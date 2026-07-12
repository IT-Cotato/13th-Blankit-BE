package com.cotato.blankit.domain.user.controller;

import com.cotato.blankit.domain.user.dto.response.UserMeResponse;
import com.cotato.blankit.domain.user.service.UserService;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자 API")
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
}

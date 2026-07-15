package com.cotato.blankit.domain.auth.controller;

import com.cotato.blankit.domain.auth.dto.request.LoginRequest;
import com.cotato.blankit.domain.auth.dto.request.RefreshTokenRequest;
import com.cotato.blankit.domain.auth.dto.request.SignupRequest;
import com.cotato.blankit.domain.auth.dto.response.LoginResponse;
import com.cotato.blankit.domain.auth.dto.response.SignupResponse;
import com.cotato.blankit.domain.auth.dto.response.TokenReissueResponse;
import com.cotato.blankit.domain.auth.service.AuthService;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "소셜 인증 기반 회원가입/로그인 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "소셜 회원가입",
            description = "외부 소셜 인증 결과를 전달받아 사용자를 생성합니다. 비밀번호 기반 가입은 지원하지 않습니다."
    )
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @Operation(
            summary = "소셜 로그인",
            description = "socialProvider + socialId로 사용자를 조회하고 JWT Access Token과 Refresh Token을 발급합니다."
    )
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(
            summary = "토큰 재발급",
            description = "Refresh Token을 검증하고 새 Access Token과 Refresh Token을 발급합니다."
    )
    @PostMapping("/reissue")
    public ApiResponse<TokenReissueResponse> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.reissue(request));
    }

    @Operation(
            summary = "로그아웃",
            description = "현재 인증 사용자의 Refresh Token을 폐기합니다. 클라이언트도 저장된 토큰을 삭제해야 로그아웃이 완료됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getUserId());
        return ApiResponse.success();
    }
}

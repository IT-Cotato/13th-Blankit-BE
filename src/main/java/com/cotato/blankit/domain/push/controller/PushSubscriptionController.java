package com.cotato.blankit.domain.push.controller;

import com.cotato.blankit.domain.push.dto.request.PushSubscriptionRequest;
import com.cotato.blankit.domain.push.dto.response.PushSubscriptionResponse;
import com.cotato.blankit.domain.push.service.PushSubscriptionService;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "웹 푸시 구독", description = "Firebase Installation ID 기반 브라우저 설치 등록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push-subscriptions")
public class PushSubscriptionController {
    private final PushSubscriptionService service;

    @Operation(summary = "FID 등록 또는 갱신", description = "동일 FID는 현재 인증 사용자 소유로 멱등 갱신합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록/갱신 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping
    public ApiResponse<PushSubscriptionResponse> register(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid PushSubscriptionRequest request) {
        return ApiResponse.success(service.register(userDetails.getUserId(), request));
    }

    @Operation(summary = "FID 구독 해제", description = "현재 사용자의 구독만 소프트 삭제(active=false)합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{subscriptionId}")
    public ApiResponse<Void> deactivate(@AuthenticationPrincipal CustomUserDetails userDetails,
                                        @PathVariable Long subscriptionId) {
        service.deactivate(userDetails.getUserId(), subscriptionId);
        return ApiResponse.success();
    }
}

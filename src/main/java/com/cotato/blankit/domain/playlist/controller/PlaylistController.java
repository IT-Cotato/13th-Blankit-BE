package com.cotato.blankit.domain.playlist.controller;

import com.cotato.blankit.domain.playlist.dto.request.PlaylistItemAddRequest;
import com.cotato.blankit.domain.playlist.dto.request.PlaylistItemOrderUpdateRequest;
import com.cotato.blankit.domain.playlist.dto.response.PlaylistResponse;
import com.cotato.blankit.domain.playlist.service.PlaylistService;
import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "플레이리스트", description = "플레이리스트 과업 관리 API (사용자당 1개)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/playlist")
public class PlaylistController {

    private final PlaylistService playlistService;

    @Operation(summary = "플레이리스트 조회",
            description = "플레이리스트와 항목 목록을 반환합니다. " +
                    "sourceMode 파라미터로 특정 모드(FIRE·BALANCE·TASTE·CLEAR·PACK30) 과업만 필터링할 수 있습니다. " +
                    "전체 칩 선택 시 파라미터를 생략하거나 null로 전달합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<PlaylistResponse> getPlaylist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "모드 필터 (생략 시 전체)", example = "FIRE")
            @RequestParam(required = false) String sourceMode) {
        return ApiResponse.success(playlistService.getPlaylist(userDetails.getUserId(), sourceMode));
    }

    @Operation(summary = "플레이리스트 과업 추가",
            description = "과업을 플레이리스트에 추가합니다. " +
                    "단건 추가(수동): taskIds에 1개, sourceMode = null. " +
                    "추천 모드 일괄 추가: taskIds에 여러 개, sourceMode = FIRE·BALANCE·TASTE·CLEAR·PACK30. " +
                    "이미 추가된 과업은 중복 추가되지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "과업 없음")
    })
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlaylistResponse> addItems(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid PlaylistItemAddRequest request) {
        return ApiResponse.success(playlistService.addItems(userDetails.getUserId(), request));
    }

    @Operation(summary = "플레이리스트 순서 변경",
            description = "드래그 핸들로 변경된 순서를 저장합니다. 변경된 모든 항목의 새 sort_order를 함께 전달합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "순서 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PatchMapping("/items/order")
    public ApiResponse<Void> updateOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid PlaylistItemOrderUpdateRequest request) {
        playlistService.updateOrder(userDetails.getUserId(), request);
        return ApiResponse.success();
    }

    @Operation(summary = "플레이리스트 과업 단건 삭제",
            description = "플레이리스트에서 특정 과업 항목 하나를 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "항목 없음")
    })
    @DeleteMapping("/items/{playlistItemId}")
    public ApiResponse<Void> deleteItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long playlistItemId) {
        playlistService.deleteItem(userDetails.getUserId(), playlistItemId);
        return ApiResponse.success();
    }

    @Operation(summary = "플레이리스트 과업 일괄 삭제",
            description = "sourceMode 파라미터로 특정 모드 과업만 일괄 삭제하거나, 파라미터 생략 시 전체 삭제합니다. " +
                    "추천 모드 체크 아이콘 클릭 시 해당 sourceMode로 호출합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping("/items")
    public ApiResponse<Void> deleteItems(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "삭제할 모드 (생략 시 전체 삭제)", example = "FIRE")
            @RequestParam(required = false) String sourceMode) {
        playlistService.deleteItems(userDetails.getUserId(), sourceMode);
        return ApiResponse.success();
    }
}

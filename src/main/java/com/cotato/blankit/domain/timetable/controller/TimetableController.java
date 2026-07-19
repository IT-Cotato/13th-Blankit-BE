package com.cotato.blankit.domain.timetable.controller;

import com.cotato.blankit.domain.timetable.dto.request.TimetableCreateRequest;
import com.cotato.blankit.domain.timetable.dto.request.TimetableUpdateRequest;
import com.cotato.blankit.domain.timetable.dto.response.TimetableResponse;
import com.cotato.blankit.domain.timetable.service.TimetableService;
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

import java.util.List;

@Tag(name = "시간표", description = "시간표 블록 관리 API (명세 4.5~4.7)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/timetable")
public class TimetableController {

    private final TimetableService timetableService;

    @Operation(summary = "시간표 목록 조회",
            description = "등록된 시간표 블록을 요일(dayOfWeek) 오름차순으로 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<List<TimetableResponse>> getTimetables(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(timetableService.getTimetables(userDetails.getUserId()));
    }

    @Operation(summary = "시간표 추가",
            description = "시간표 블록을 추가합니다. 기존 블록과 시간이 겹치면 오류를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "시간 겹침")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimetableResponse> createTimetable(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid TimetableCreateRequest request) {
        return ApiResponse.success(timetableService.createTimetable(userDetails.getUserId(), request));
    }

    @Operation(summary = "시간표 수정",
            description = "시간표 블록 정보를 수정합니다. null 필드는 변경하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "시간표 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "시간 겹침")
    })
    @PatchMapping("/{timetableId}")
    public ApiResponse<TimetableResponse> updateTimetable(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long timetableId,
            @RequestBody @Valid TimetableUpdateRequest request) {
        return ApiResponse.success(timetableService.updateTimetable(userDetails.getUserId(), timetableId, request));
    }

    @Operation(summary = "시간표 단건 삭제",
            description = "선택한 시간표 블록을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "시간표 없음")
    })
    @DeleteMapping("/{timetableId}")
    public ApiResponse<Void> deleteTimetable(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long timetableId) {
        timetableService.deleteTimetable(userDetails.getUserId(), timetableId);
        return ApiResponse.success();
    }

    @Operation(summary = "시간표 전체 초기화",
            description = "등록된 모든 시간표 블록을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초기화 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping
    public ApiResponse<Void> deleteAllTimetables(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        timetableService.deleteAllTimetables(userDetails.getUserId());
        return ApiResponse.success();
    }
}

package com.cotato.blankit.domain.timetable.controller;

import com.cotato.blankit.domain.timetable.dto.request.TimetableCreateRequest;
import com.cotato.blankit.domain.timetable.dto.request.TimetableUpdateRequest;
import com.cotato.blankit.domain.timetable.dto.response.TimetableResponse;
import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import com.cotato.blankit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

@NotImplementedYet
@Tag(name = "시간표", description = "시간표 블록 관리 API (명세 4.5~4.7)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/timetable")
public class TimetableController {

    @Operation(summary = "시간표 목록 조회",
            description = "등록된 시간표 블록을 요일(dayOfWeek) 오름차순으로 반환합니다. " +
                    "표시 범위(timetableStartTime~timetableEndTime)는 사용자 프로필에서 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<List<TimetableResponse>> getTimetables() {
        return ApiResponse.success(List.of(
                new TimetableResponse(1L, 1, LocalTime.of(9, 0), LocalTime.of(10, 30),
                        "알고리즘 강의", "공학관 101호", "#7B5EA7"),
                new TimetableResponse(2L, 3, LocalTime.of(13, 0), LocalTime.of(14, 30),
                        "운영체제 강의", "공학관 202호", "#5C9EFF"),
                new TimetableResponse(3L, 5, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        "영어 회화", "어학원 B동 3층", "#5CFF8A")
        ));
    }

    @Operation(summary = "시간표 추가",
            description = "시간표 블록을 추가합니다. 기존 블록과 시간이 겹치면 오류를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류 또는 시간 겹침"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimetableResponse> createTimetable(@RequestBody @Valid TimetableCreateRequest request) {
        return ApiResponse.success(new TimetableResponse(
                4L, request.dayOfWeek(), request.startTime(), request.endTime(),
                request.title(), request.place(), request.color()
        ));
    }

    @Operation(summary = "시간표 수정",
            description = "시간표 블록 정보를 수정합니다. null 필드는 변경하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 오류 또는 시간 겹침"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "시간표 없음")
    })
    @PatchMapping("/{timetableId}")
    public ApiResponse<TimetableResponse> updateTimetable(
            @PathVariable Long timetableId,
            @RequestBody @Valid TimetableUpdateRequest request) {
        return ApiResponse.success(new TimetableResponse(
                timetableId,
                request.dayOfWeek() != null ? request.dayOfWeek() : 1,
                request.startTime() != null ? request.startTime() : LocalTime.of(9, 0),
                request.endTime() != null ? request.endTime() : LocalTime.of(10, 30),
                request.title() != null ? request.title() : "알고리즘 강의",
                request.place(), request.color() != null ? request.color() : "#7B5EA7"
        ));
    }

    @Operation(summary = "시간표 단건 삭제",
            description = "선택한 시간표 블록을 삭제합니다. 같은 이름의 다른 블록은 삭제되지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "시간표 없음")
    })
    @DeleteMapping("/{timetableId}")
    public ApiResponse<Void> deleteTimetable(@PathVariable Long timetableId) {
        return ApiResponse.success();
    }

    @Operation(summary = "시간표 전체 초기화",
            description = "등록된 모든 시간표 블록을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초기화 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @DeleteMapping
    public ApiResponse<Void> deleteAllTimetables() {
        return ApiResponse.success();
    }
}

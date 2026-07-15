package com.cotato.blankit.domain.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "과업 등록 화면 초기값 응답")
public record TaskFormOptionsResponse(
        @Schema(description = "기본 카테고리 ID. 활성 카테고리가 없으면 null입니다.", nullable = true)
        Long defaultCategoryId,
        @Schema(description = "기본 알림 오프셋(분)", example = "1440")
        int defaultReminderOffsetMinutes,
        @Schema(description = "반복 기본값. false이면 repeatRule을 생성하지 않습니다.", example = "false")
        boolean defaultRepeatEnabled,
        @Schema(description = "활성 카테고리 목록")
        List<CategoryResponse> categories,
        @Schema(description = "알림 설정 가능 범위")
        ReminderRangeResponse reminderRange,
        @Schema(description = "알림 선택지(분). 10분 전, 1시간 전, 1일 전, 3일 전, 일주일 전", example = "[10, 60, 1440, 4320, 10080]")
        List<Integer> reminderOptions
) {
}

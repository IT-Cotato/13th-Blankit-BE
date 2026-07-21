package com.cotato.blankit.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@Schema(description = "내 프로필 응답")
public record UserProfileResponse(

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "닉네임", example = "옥준승")
        String nickname,

        @Schema(description = "이메일 (소셜 계정에 이메일이 없으면 null)", example = "junseung0305@gmail.com")
        String email,

        @Schema(description = "프로필 이미지 URL (null 허용)", example = "https://example.com/profile.jpg")
        String profileImageUrl,

        @Schema(description = "시간표 표시 시작 시간 (기본 08:00)", example = "08:00:00")
        LocalTime timetableStartTime,

        @Schema(description = "시간표 표시 종료 시간 (기본 00:00)", example = "00:00:00")
        LocalTime timetableEndTime
) {
}

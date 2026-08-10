package com.cotato.blankit.domain.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "에브리타임 시간표 가져오기 요청")
public record EverytimeImportRequest(

        @Schema(description = "에브리타임 공유 URL", example = "https://everytime.kr/@abcd1234")
        @NotBlank(message = "URL은 필수입니다.")
        @Pattern(regexp = "^https://everytime\\.kr/@[A-Za-z0-9_-]+$", message = "유효하지 않은 에브리타임 URL입니다.")
        String url
) {
}

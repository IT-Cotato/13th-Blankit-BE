package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "과업 생성 요청")
public record TaskCreateRequest(
        @Schema(description = "과업명", example = "알고리즘 과제 제출", maxLength = 255)
        @NotBlank(message = "과업명은 필수입니다.")
        @Size(max = 255, message = "과업명은 최대 255자까지 입력할 수 있습니다.")
        String title,

        @Schema(description = "일반 과업 마감일. 반복 과업에서는 서버가 repeatRule 기준으로 deadline을 계산합니다.", example = "2026-08-12", nullable = true)
        LocalDate deadline,

        @Schema(description = "알림 오프셋(분). 생략 시 1440분입니다. 허용값: 1440, 4320, 10080", example = "1440", allowableValues = {"1440", "4320", "10080"})
        Integer notifyBefore,

        @Schema(description = "알림 활성화 여부. 생략 시 true입니다.", example = "true")
        Boolean notificationEnabled,

        @Schema(description = "반복 규칙. 반복하지 않으면 전달하지 않습니다.")
        RepeatRuleRequest repeatRule,

        @Schema(description = "카테고리 ID. 생략 시 가장 먼저 생성된 활성 카테고리를 사용합니다.", example = "1")
        Long categoryId,

        @Schema(description = "과업을 나눈 챕터 제목 목록. 전달 순서대로 저장합니다.", example = "[\"1장 자료구조\", \"2장 알고리즘\"]")
        @NotEmpty(message = "챕터는 하나 이상 등록해야 합니다.")
        List<@NotBlank(message = "챕터 제목은 필수입니다.") @Size(max = 100, message = "챕터 제목은 최대 100자까지 입력할 수 있습니다.") String> chapters
) {
}

package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "과업 생성 요청")
public record TaskCreateRequest(
        @Schema(description = "과업명", example = "알고리즘 과제 제출", maxLength = 255)
        @NotBlank(message = "과업명은 필수입니다.")
        @Size(max = 255, message = "과업명은 최대 255자까지 입력할 수 있습니다.")
        String title,

        @Schema(description = "일반 과업 마감일. 반복 과업에서는 서버가 repeatRule 기준으로 deadline을 계산합니다.", example = "2026-08-12", nullable = true)
        LocalDate deadline,

        @Schema(description = "알림 오프셋(분). 생략 시 1440분입니다. 허용값: 10, 60, 1440, 4320, 10080", example = "1440", allowableValues = {"10", "60", "1440", "4320", "10080"})
        Integer notifyBefore,

        @Schema(description = "알림 활성화 여부. 생략 시 true입니다.", example = "true")
        Boolean notificationEnabled,

        @Schema(description = "반복 규칙. 반복하지 않으면 전달하지 않습니다.")
        RepeatRuleRequest repeatRule,

        @Schema(description = "카테고리 ID. 생략 시 가장 먼저 생성된 활성 카테고리를 사용합니다.", example = "1")
        Long categoryId,

        @Schema(description = "직접 입력한 예상 소요 시간(분). similarTaskId가 없을 때 task.estimated_time에 저장합니다.", example = "90", nullable = true)
        @PositiveOrZero(message = "예상 시간은 0 이상이어야 합니다.")
        Integer estimatedTime,

        @Schema(description = "비슷한 이전 완료 과업 ID. 비슷한 경험이 없으면 전달하지 않거나 null로 전달합니다.", example = "12", nullable = true)
        Long similarTaskId
) {
}

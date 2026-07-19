package com.cotato.blankit.domain.task.dto.request;

import com.cotato.blankit.domain.task.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "과업 수정 요청. 전달된 필드만 수정합니다.")
public record TaskUpdateRequest(
        @Schema(description = "과업명", example = "알고리즘 과제 최종 제출", maxLength = 255)
        @Size(max = 255, message = "과업명은 최대 255자까지 입력할 수 있습니다.")
        String title,

        @Schema(description = "마감일", example = "2026-08-12")
        LocalDate deadline,

        @Schema(description = "알림 오프셋(분). 허용값: 10, 60, 1440, 4320, 10080", example = "1440", allowableValues = {"10", "60", "1440", "4320", "10080"})
        Integer notifyBefore,

        @Schema(description = "알림 활성화 여부")
        Boolean notificationEnabled,

        @Schema(description = "반복 규칙. 전달 시 기존 반복 규칙 전체를 교체합니다.")
        RepeatRuleRequest repeatRule,

        @Schema(description = "true이면 기존 반복 규칙을 삭제합니다.", example = "false")
        Boolean clearRepeatRule,

        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,

        @Schema(description = "과업 상태", example = "DONE", allowableValues = {"TODO", "IN_PROGRESS", "DONE"})
        TaskStatus status,

        @Schema(description = "중요 표시", example = "false")
        Boolean starred,

        @Schema(description = "비슷한 이전 완료 과업 ID", example = "12", nullable = true)
        Long similarTaskId,

        @Schema(description = "true이면 similarTask 연결을 해제합니다.", example = "false")
        Boolean clearSimilarTask
) {
}

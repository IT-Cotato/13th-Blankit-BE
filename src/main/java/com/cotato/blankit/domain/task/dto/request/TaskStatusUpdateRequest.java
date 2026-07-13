package com.cotato.blankit.domain.task.dto.request;

import com.cotato.blankit.domain.task.entity.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "과업 상태 변경 요청")
public record TaskStatusUpdateRequest(

        @Schema(description = "변경할 상태 (TODO·IN_PROGRESS·DONE)", example = "DONE")
        @NotNull(message = "상태는 필수입니다.")
        TaskStatus status
) {
}

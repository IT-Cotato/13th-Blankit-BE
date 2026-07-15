package com.cotato.blankit.domain.feedback.dto.request;

import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "세션 상태 변경 요청")
public record SessionStatusUpdateRequest(

        @Schema(description = "변경할 세션 상태 (PLAYING=재생, PAUSED=일시정지, DONE=종료)", example = "PLAYING")
        @NotNull(message = "세션 상태는 필수입니다.")
        TaskSessionStatus status
) {
}

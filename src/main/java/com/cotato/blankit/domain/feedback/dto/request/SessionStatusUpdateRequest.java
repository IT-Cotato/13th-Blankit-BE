package com.cotato.blankit.domain.feedback.dto.request;

import com.cotato.blankit.domain.feedback.entity.enums.TaskSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "세션 상태 변경 요청")
public record SessionStatusUpdateRequest(

        @Schema(description = "변경할 세션 상태 (PLAYING=재생, PAUSED=일시정지, DONE=종료)", example = "PLAYING")
        @NotNull(message = "세션 상태는 필수입니다.")
        TaskSessionStatus status,

        @Schema(description = "클라이언트 기준 현재까지 누적된 소요 시간 (초). 상태 변경마다 최신 값을 함께 전송해야 합니다.", example = "1800")
        @NotNull(message = "소요 시간은 필수입니다.")
        @Min(value = 0, message = "소요 시간은 0 이상이어야 합니다.")
        Integer elapsedTime
) {
}

package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "중요 표시 설정 요청")
public record TaskStarUpdateRequest(

        @Schema(description = "중요 표시 여부 (멱등 보장: 원하는 최종 상태를 전송)", example = "true")
        @NotNull(message = "isStarred 값은 필수입니다.")
        Boolean isStarred
) {
}

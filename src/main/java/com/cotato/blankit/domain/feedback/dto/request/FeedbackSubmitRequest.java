package com.cotato.blankit.domain.feedback.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "피드백 제출 요청 (progressRate·memo 중 최소 하나 필요, isDraft=true 시 임시저장)")
public record FeedbackSubmitRequest(

        @Schema(description = "진척도 (0~100, 세부 단계가 있으면 자동 계산되어 무시됨)", example = "40")
        @Min(value = 0, message = "진척도는 0 이상이어야 합니다.")
        @Max(value = 100, message = "진척도는 100 이하여야 합니다.")
        Integer progressRate,

        @Schema(description = "어디까지 했는지 메모", example = "2단원까지 정리 완료, 3단원부터 이어서")
        String memo,

        @Schema(description = "임시저장 여부 (true=임시저장, false=최종 제출, 통계 계산에서 draft 제외)", example = "false")
        boolean isDraft
) {
}

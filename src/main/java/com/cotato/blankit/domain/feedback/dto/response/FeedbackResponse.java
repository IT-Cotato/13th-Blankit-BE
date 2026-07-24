package com.cotato.blankit.domain.feedback.dto.response;

import com.cotato.blankit.domain.feedback.entity.Feedback;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "피드백 응답")
public record FeedbackResponse(

        @Schema(description = "피드백 ID", example = "1")
        Long feedbackId,

        @Schema(description = "세션 ID", example = "1")
        Long taskSessionId,

        @Schema(description = "과업 ID", example = "1")
        Long taskId,

        @Schema(description = "진척도 (0~100, null = 메모만 작성)", example = "40")
        Integer progressRate,

        @Schema(description = "메모 (null = 진척도만 입력)", example = "2단원까지 정리 완료, 3단원부터 이어서")
        String memo,

        @Schema(description = "100% 완료 여부", example = "false")
        boolean isCompleted,

        @Schema(description = "임시저장 여부", example = "false")
        boolean isDraft
) {
    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getFeedbackId(),
                feedback.getTaskSession().getTaskSessionId(),
                feedback.getTask().getId(),
                feedback.getProgressRate(),
                feedback.getMemo(),
                feedback.isCompleted(),
                feedback.isDraft()
        );
    }
}

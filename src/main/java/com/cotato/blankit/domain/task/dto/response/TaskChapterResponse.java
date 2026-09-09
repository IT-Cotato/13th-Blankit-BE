package com.cotato.blankit.domain.task.dto.response;

import com.cotato.blankit.domain.task.entity.TaskStep;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "과업 챕터 응답")
public record TaskChapterResponse(
        @Schema(description = "챕터 ID", example = "1")
        Long chapterId,
        @Schema(description = "챕터 제목", example = "1장 자료구조")
        String title,
        @Schema(description = "챕터 진척도 (0~100)", example = "0")
        int progressRate
) {

    public static TaskChapterResponse from(TaskStep chapter) {
        return new TaskChapterResponse(
                chapter.getTaskStepId(),
                chapter.getTitle(),
                chapter.getProgressRate()
        );
    }
}

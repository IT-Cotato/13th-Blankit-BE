package com.cotato.blankit.domain.search.dto.response;

import com.cotato.blankit.domain.task.entity.TaskPriority;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.entity.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "과업 검색 결과 응답 (명세 2.5.3)")
public record SearchResultResponse(

        @Schema(description = "검색 결과 총 개수", example = "2")
        int totalCount,

        @Schema(description = "검색된 과업 목록 (categoryId 포함 — 프론트에서 2.16 카테고리 그룹핑에 활용)")
        List<SearchTaskItem> tasks
) {

    private static final int DEFAULT_PROGRESS_RATE = 0;

    @Schema(description = "검색 결과 과업 항목")
    public record SearchTaskItem(

            @Schema(description = "과업 ID", example = "1")
            Long taskId,

            @Schema(description = "과업명", example = "수학 기말고사 준비")
            String title,

            @Schema(description = "카테고리 ID (프론트 그룹핑 기준)", example = "1")
            Long categoryId,

            @Schema(description = "카테고리명", example = "학업")
            String categoryName,

            @Schema(description = "카테고리 색상 (HEX)", example = "#FF5C5C")
            String categoryColor,

            @Schema(description = "우선순위", example = "HIGH")
            TaskPriority priority,

            @Schema(description = "마감일", example = "2026-07-20")
            LocalDate deadline,

            @Schema(description = "과업 상태", example = "IN_PROGRESS")
            TaskStatus status,

            @Schema(description = "전체 진척도 (0~100)", example = "40")
            int progressRate
    ) {
    }

    public static SearchResultResponse from(Page<Task> tasks) {
        return new SearchResultResponse(
                (int) Math.min(tasks.getTotalElements(), Integer.MAX_VALUE),
                tasks.getContent().stream()
                        .map(SearchResultResponse::from)
                        .toList()
        );
    }

    public static SearchTaskItem from(Task task) {
        return new SearchTaskItem(
                task.getId(),
                task.getTitle(),
                task.getCategory().getId(),
                task.getCategory().getName(),
                task.getCategory().getColor(),
                task.getPriority(),
                task.getDeadline(),
                task.getStatus(),
                resolveProgressRate(task)
        );
    }

    private static int resolveProgressRate(Task task) {
        // TODO: 진행률 산정 정책 확정 후 Feedback/TaskStep 기반 일괄 조회로 교체 필요
        return DEFAULT_PROGRESS_RATE;
    }
}

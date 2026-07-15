package com.cotato.blankit.domain.task.dto.response;

import com.cotato.blankit.domain.task.entity.Category;
import com.cotato.blankit.domain.task.entity.CategoryColor;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리 응답")
public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,

        @Schema(description = "카테고리명", example = "학교")
        String categoryName,

        @Schema(description = "카테고리 색상", example = "BLUE")
        CategoryColor color
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getColor());
    }
}

package com.cotato.blankit.domain.category.dto.response;

import com.cotato.blankit.domain.category.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TaskCategoryResponse", description = "카테고리 응답")
public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,

        @Schema(description = "카테고리명", example = "학교")
        String categoryName,

        @Schema(description = "카테고리 색상값", example = "#FFB85C")
        String color
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getColor());
    }
}

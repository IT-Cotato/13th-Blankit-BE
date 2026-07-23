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
        String color,

        @Schema(description = "프론트엔드에서 관리하는 카테고리 아이콘 식별 키", example = "book")
        String iconKey
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getColor(), category.getIconKey());
    }
}

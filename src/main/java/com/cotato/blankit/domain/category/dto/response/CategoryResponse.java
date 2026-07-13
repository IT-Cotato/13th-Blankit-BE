package com.cotato.blankit.domain.category.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리 응답")
public record CategoryResponse(

        @Schema(description = "카테고리 ID", example = "1")
        Long categoryId,

        @Schema(description = "카테고리 이름", example = "학업")
        String name,

        @Schema(description = "카테고리 색상 (HEX)", example = "#FF5C5C")
        String color,

        @Schema(description = "정렬 순서", example = "0")
        int sortOrder,

        @Schema(description = "기본 카테고리 여부 (학업·일상·기념일)", example = "true")
        boolean isDefault,

        @Schema(description = "소프트 삭제 여부", example = "false")
        boolean isDeleted
) {
}

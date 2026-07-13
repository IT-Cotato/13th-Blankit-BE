package com.cotato.blankit.domain.category.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "카테고리 수정 요청")
public record CategoryUpdateRequest(

        @Schema(description = "카테고리 이름", example = "취미")
        @Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
        String name,

        @Schema(description = "카테고리 색상 (HEX, 동일 사용자 내 중복 불가)", example = "#5CFF8A")
        @Size(max = 20, message = "색상은 20자 이하여야 합니다.")
        String color,

        @Schema(description = "정렬 순서", example = "3")
        Integer sortOrder
) {
}

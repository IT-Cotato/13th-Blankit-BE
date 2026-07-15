package com.cotato.blankit.domain.category.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "카테고리 추가 요청")
public record CategoryCreateRequest(

        @Schema(description = "카테고리 이름", example = "운동")
        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
        String name,

        @Schema(description = "카테고리 색상 (HEX, 동일 사용자 내 중복 불가)", example = "#FFB85C")
        @NotBlank(message = "색상은 필수입니다.")
        @Size(max = 20, message = "색상은 20자 이하여야 합니다.")
        String color,

        @Schema(description = "정렬 순서", example = "4")
        @NotNull(message = "정렬 순서는 필수입니다.")
        Integer sortOrder
) {
}

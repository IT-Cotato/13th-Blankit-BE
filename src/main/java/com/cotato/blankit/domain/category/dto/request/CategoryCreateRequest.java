package com.cotato.blankit.domain.category.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "TaskCategoryCreateRequest", description = "카테고리 생성 요청")
public record CategoryCreateRequest(
        @Schema(description = "카테고리명", example = "운동", maxLength = 100)
        @NotBlank(message = "카테고리명은 필수입니다.")
        @Size(max = 100, message = "카테고리명은 최대 100자까지 입력할 수 있습니다.")
        String name,

        @Schema(description = "카테고리 색상 HEX 값. 같은 사용자 내 활성 카테고리와 중복될 수 없습니다.", example = "#FFB85C", pattern = "^#[0-9A-Fa-f]{6}$", maxLength = 20)
        @NotBlank(message = "카테고리 색상은 필수입니다.")
        @Size(max = 20, message = "카테고리 색상은 최대 20자까지 입력할 수 있습니다.")
        String color,

        @Schema(description = "프론트엔드에서 관리하는 카테고리 아이콘 식별 키", example = "book", maxLength = 100)
        @NotBlank(message = "카테고리 아이콘 키는 필수입니다.")
        @Size(max = 100, message = "카테고리 아이콘 키는 최대 100자까지 입력할 수 있습니다.")
        String iconKey
) {
}

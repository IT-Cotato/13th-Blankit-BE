package com.cotato.blankit.domain.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "TaskCategoryUpdateRequest", description = "카테고리 수정 요청. 전달된 필드만 수정합니다.")
public record CategoryUpdateRequest(
        @Schema(description = "카테고리명", example = "학교", maxLength = 100)
        @Size(max = 100, message = "카테고리명은 최대 100자까지 입력할 수 있습니다.")
        String name,

        @Schema(description = "카테고리 색상 HEX 값. 같은 사용자 내 활성 카테고리와 중복될 수 없습니다.", example = "#5C9EFF", pattern = "^#[0-9A-Fa-f]{6}$", maxLength = 20)
        @Size(max = 20, message = "카테고리 색상은 최대 20자까지 입력할 수 있습니다.")
        String color
) {
}

package com.cotato.blankit.domain.task.dto.request;

import com.cotato.blankit.domain.task.entity.CategoryColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "카테고리 수정 요청. 전달된 필드만 수정합니다.")
public record CategoryUpdateRequest(
        @Schema(description = "카테고리명", example = "학교", maxLength = 100)
        @Size(max = 100, message = "카테고리명은 최대 100자까지 입력할 수 있습니다.")
        String name,

        @Schema(description = "카테고리 색상", example = "BLUE")
        CategoryColor color
) {
}

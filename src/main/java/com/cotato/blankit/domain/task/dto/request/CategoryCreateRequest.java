package com.cotato.blankit.domain.task.dto.request;

import com.cotato.blankit.domain.task.entity.CategoryColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "카테고리 생성 요청")
public record CategoryCreateRequest(
        @Schema(description = "카테고리명", example = "운동", maxLength = 100)
        @NotBlank(message = "카테고리명은 필수입니다.")
        @Size(max = 100, message = "카테고리명은 최대 100자까지 입력할 수 있습니다.")
        String name,

        @Schema(description = "카테고리 색상", example = "GREEN")
        @NotNull(message = "카테고리 색상은 필수입니다.")
        CategoryColor color
) {
}

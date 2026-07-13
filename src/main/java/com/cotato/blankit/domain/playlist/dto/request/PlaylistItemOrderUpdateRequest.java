package com.cotato.blankit.domain.playlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "플레이리스트 순서 변경 요청 (드래그 핸들)")
public record PlaylistItemOrderUpdateRequest(

        @Schema(description = "변경된 순서 목록 (전체 항목의 새 sort_order를 전달)")
        @NotEmpty(message = "순서 변경 항목은 최소 1개 이상이어야 합니다.")
        List<PlaylistItemOrder> items
) {

    @Schema(description = "순서 항목")
    public record PlaylistItemOrder(

            @Schema(description = "플레이리스트 항목 ID", example = "1")
            @NotNull(message = "playlistItemId는 필수입니다.")
            Long playlistItemId,

            @Schema(description = "새 정렬 순서", example = "0")
            @NotNull(message = "sortOrder는 필수입니다.")
            Integer sortOrder
    ) {
    }
}

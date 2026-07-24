package com.cotato.blankit.domain.playlist.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "플레이리스트 응답")
public record PlaylistResponse(

        @Schema(description = "플레이리스트 ID", example = "1")
        Long playlistId,

        @Schema(description = "전체 항목 수 (필터 적용 전)", example = "5")
        int totalCount,

        @Schema(description = "과업 항목 목록 (source_mode 필터 적용 결과)")
        List<PlaylistItemResponse> items
) {

    @Schema(description = "플레이리스트 항목")
    public record PlaylistItemResponse(

            @Schema(description = "플레이리스트 항목 ID", example = "1")
            Long playlistItemId,

            @Schema(description = "과업 ID", example = "1")
            Long taskId,

            @Schema(description = "과업명", example = "기말고사 준비")
            String title,

            @Schema(description = "카테고리명", example = "학업")
            String categoryName,

            @Schema(description = "카테고리 색상 (HEX)", example = "#FF5C5C")
            String categoryColor,

            @Schema(description = "카테고리 아이콘 식별 키", example = "book")
            String categoryIconKey,

            @Schema(description = "정렬 순서", example = "0")
            int sortOrder,

            @Schema(description = "추가 경로 모드 (수동 추가 시 null)", example = "FIRE")
            String sourceMode
    ) {
    }
}

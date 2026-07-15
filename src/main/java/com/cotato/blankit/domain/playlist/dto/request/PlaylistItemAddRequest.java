package com.cotato.blankit.domain.playlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "플레이리스트 과업 추가 요청 (단건·모드 일괄 공통 사용)")
public record PlaylistItemAddRequest(

        @Schema(description = "추가할 과업 ID 목록 (단건 추가 시 1개, 모드 다운로드 시 여러 개)", example = "[1, 2, 3]")
        @NotEmpty(message = "추가할 과업 ID는 최소 1개 이상이어야 합니다.")
        List<Long> taskIds,

        @Schema(description = "추가 경로 모드 (수동 추가 시 null, 추천 모드 다운로드 시 FIRE·BALANCE·TASTE·CLEAR·PACK30)",
                example = "FIRE")
        String sourceMode
) {
}

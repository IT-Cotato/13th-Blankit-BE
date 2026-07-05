package com.cotato.blankit.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.cotato.blankit.global.exception.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String code,
        String message,
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "요청이 성공했습니다.", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>("SUCCESS", "요청이 성공했습니다.", null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
}

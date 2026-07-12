package com.cotato.blankit.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력값입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."),

    // Auth/User
    DUPLICATE_SOCIAL_ACCOUNT(HttpStatus.CONFLICT, "DUPLICATE_SOCIAL_ACCOUNT", "이미 가입된 소셜 계정입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "인증 정보가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다."),
    REFRESH_TOKEN_CONFLICT(HttpStatus.CONFLICT, "REFRESH_TOKEN_CONFLICT", "리프레시 토큰 처리 중 충돌이 발생했습니다. 다시 시도해주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

    // Task
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "과업을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."),
    INVALID_TASK_TITLE(HttpStatus.BAD_REQUEST, "INVALID_TASK_TITLE", "과업명이 올바르지 않습니다."),
    INVALID_REFERENCE_TASK(HttpStatus.BAD_REQUEST, "INVALID_REFERENCE_TASK", "참고 과업이 올바르지 않습니다."),
    REFERENCE_TASK_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "REFERENCE_TASK_NOT_COMPLETED", "완료된 과업만 참고 과업으로 설정할 수 있습니다."),
    SELF_REFERENCE_TASK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SELF_REFERENCE_TASK_NOT_ALLOWED", "자기 자신을 참고 과업으로 설정할 수 없습니다."),
    CYCLIC_REFERENCE_TASK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CYCLIC_REFERENCE_TASK_NOT_ALLOWED", "순환 참고 과업은 설정할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

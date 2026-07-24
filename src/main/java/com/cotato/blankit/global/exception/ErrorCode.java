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
    INVALID_TIMETABLE_SETTINGS(HttpStatus.BAD_REQUEST, "INVALID_TIMETABLE_SETTINGS", "시작 시간은 종료 시간보다 빨라야 합니다."),

    // Task
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "과업을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."),
    CATEGORY_REQUIRED(HttpStatus.BAD_REQUEST, "CATEGORY_REQUIRED", "사용 가능한 카테고리가 필요합니다."),
    CATEGORY_COLOR_ALREADY_USED(HttpStatus.CONFLICT, "CATEGORY_COLOR_ALREADY_USED", "이미 사용 중인 카테고리 색상입니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "과업이 연결된 카테고리는 삭제할 수 없습니다."),
    INVALID_TASK_TITLE(HttpStatus.BAD_REQUEST, "INVALID_TASK_TITLE", "과업명이 올바르지 않습니다."),
    INVALID_DUE_DATE(HttpStatus.BAD_REQUEST, "INVALID_DUE_DATE", "마감일이 올바르지 않습니다."),
    INVALID_REMINDER_OFFSET(HttpStatus.BAD_REQUEST, "INVALID_REMINDER_OFFSET", "알림 설정이 올바르지 않습니다."),
    INVALID_RECURRENCE(HttpStatus.BAD_REQUEST, "INVALID_RECURRENCE", "반복 설정이 올바르지 않습니다."),
    INVALID_RECURRENCE_DATE_RANGE(HttpStatus.BAD_REQUEST, "INVALID_RECURRENCE_DATE_RANGE", "반복 기간이 올바르지 않습니다."),
    INVALID_SIMILAR_TASK(HttpStatus.BAD_REQUEST, "INVALID_SIMILAR_TASK", "비슷한 과업이 올바르지 않습니다."),
    SIMILAR_TASK_NOT_DONE(HttpStatus.BAD_REQUEST, "SIMILAR_TASK_NOT_DONE", "완료된 과업만 비슷한 과업으로 설정할 수 있습니다."),
    SELF_SIMILAR_TASK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SELF_SIMILAR_TASK_NOT_ALLOWED", "자기 자신을 비슷한 과업으로 설정할 수 없습니다."),
    CYCLIC_SIMILAR_TASK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CYCLIC_SIMILAR_TASK_NOT_ALLOWED", "순환 비슷한 과업은 설정할 수 없습니다."),

    // Timetable
    TIMETABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "TIMETABLE_NOT_FOUND", "시간표를 찾을 수 없습니다."),
    TIMETABLE_TIME_CONFLICT(HttpStatus.CONFLICT, "TIMETABLE_TIME_CONFLICT", "기존 시간표와 시간이 겹칩니다."),
    TIMETABLE_INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "TIMETABLE_INVALID_TIME_RANGE", "시작 시간은 종료 시간보다 빨라야 합니다."),
    TIMETABLE_INVALID_TIME_UNIT(HttpStatus.BAD_REQUEST, "TIMETABLE_INVALID_TIME_UNIT", "시간은 30분 단위여야 합니다."),

    // Feedback & Session
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."),
    SESSION_ALREADY_PLAYING(HttpStatus.CONFLICT, "SESSION_ALREADY_PLAYING", "이미 실행 중인 세션이 있습니다."),
    SESSION_ALREADY_DONE(HttpStatus.CONFLICT, "SESSION_ALREADY_DONE", "이미 완료된 세션입니다."),
    FEEDBACK_DUPLICATE(HttpStatus.CONFLICT, "FEEDBACK_DUPLICATE", "이미 피드백이 존재합니다."),

    // Playlist
    PLAYLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAYLIST_ITEM_NOT_FOUND", "플레이리스트 항목을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

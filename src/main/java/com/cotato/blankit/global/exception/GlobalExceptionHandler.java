package com.cotato.blankit.global.exception;

import com.cotato.blankit.global.response.ApiResponse;
import com.cotato.blankit.global.response.FieldErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleValidationException(MethodArgumentNotValidException e) {
        List<FieldErrorResponse> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorResponse(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ApiResponse.failWithData(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}

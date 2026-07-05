package com.cotato.blankit.global.response;

public record FieldErrorResponse(
        String field,
        String message
) {
}

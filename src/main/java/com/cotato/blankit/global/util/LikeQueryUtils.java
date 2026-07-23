package com.cotato.blankit.global.util;

import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;

public final class LikeQueryUtils {

    private static final int MAX_KEYWORD_LENGTH = 100;

    private LikeQueryUtils() {
    }

    public static String normalizeRequiredKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return validateLength(keyword.trim());
    }

    public static String normalizeOptionalKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return validateLength(keyword.trim());
    }

    public static String normalizeAndEscapeRequiredKeyword(String keyword) {
        return escapeLikeKeyword(normalizeRequiredKeyword(keyword));
    }

    public static String normalizeAndEscapeOptionalKeyword(String keyword) {
        String normalized = normalizeOptionalKeyword(keyword);
        return normalized == null ? null : escapeLikeKeyword(normalized);
    }

    public static String escapeLikeKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static String validateLength(String keyword) {
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return keyword;
    }
}

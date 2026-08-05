package com.cotato.blankit.domain.feedback.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackSubmitRequestTest {

    private static final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<FeedbackSubmitRequest>> validate(FeedbackSubmitRequest req) {
        return validator.validate(req);
    }

    // ─── progressRate 범위 검증 ───────────────────────────────────────────────

    @Nested
    @DisplayName("progressRate 범위 검증")
    class ProgressRateRange {

        @ParameterizedTest
        @DisplayName("progressRate 경계값: -1은 무효, 0·100은 유효, 101은 무효")
        @CsvSource({
                "-1,  false",
                " 0,  true",
                "100, true",
                "101, false"
        })
        void progressRate_boundary(int rate, boolean expectedValid) {
            // given
            FeedbackSubmitRequest req = new FeedbackSubmitRequest(rate, null, false, null);
            // when
            boolean hasRangeViolation = validate(req).stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("progressRate"));
            // then
            assertThat(!hasRangeViolation).isEqualTo(expectedValid);
        }
    }

    // ─── StepProgressItem 필드 검증 ───────────────────────────────────────────

    @Nested
    @DisplayName("StepProgressItem 필드 검증")
    class StepProgressItemValidation {

        @Test
        @DisplayName("stepId가 null이면 위반이 발생한다")
        void stepItem_nullStepId_invalid() {
            // given
            FeedbackSubmitRequest req = new FeedbackSubmitRequest(
                    null, null, false,
                    List.of(new FeedbackSubmitRequest.StepProgressItem(null, 50))
            );
            // when
            Set<ConstraintViolation<FeedbackSubmitRequest>> violations = validate(req);
            // then
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().contains("stepId"));
        }

        @ParameterizedTest
        @DisplayName("StepProgressItem.progressRate 경계값: -1은 무효, 0·100은 유효, 101은 무효")
        @CsvSource({
                "-1,  false",
                " 0,  true",
                "100, true",
                "101, false"
        })
        void stepItem_progressRate_boundary(int rate, boolean expectedValid) {
            // given
            FeedbackSubmitRequest req = new FeedbackSubmitRequest(
                    null, null, false,
                    List.of(new FeedbackSubmitRequest.StepProgressItem(1L, rate))
            );
            // when
            boolean hasRangeViolation = validate(req).stream()
                    .anyMatch(v -> v.getPropertyPath().toString().contains("steps[0].progressRate"));
            // then
            assertThat(!hasRangeViolation).isEqualTo(expectedValid);
        }
    }
}

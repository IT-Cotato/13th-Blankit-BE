package com.cotato.blankit.global.util;

import com.cotato.blankit.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeQueryUtilsTest {

    @Test
    void normalizeRequiredKeywordTrimsAndRejectsBlankOrTooLongKeyword() {
        assertThat(LikeQueryUtils.normalizeRequiredKeyword("  수학  ")).isEqualTo("수학");
        assertThatThrownBy(() -> LikeQueryUtils.normalizeRequiredKeyword(null))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> LikeQueryUtils.normalizeRequiredKeyword("   "))
                .isInstanceOf(CustomException.class);
        assertThat(LikeQueryUtils.normalizeRequiredKeyword("a".repeat(100))).hasSize(100);
        assertThatThrownBy(() -> LikeQueryUtils.normalizeRequiredKeyword("a".repeat(101)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void normalizeOptionalKeywordReturnsNullForBlankAndTrimsNormalKeyword() {
        assertThat(LikeQueryUtils.normalizeOptionalKeyword(null)).isNull();
        assertThat(LikeQueryUtils.normalizeOptionalKeyword("   ")).isNull();
        assertThat(LikeQueryUtils.normalizeOptionalKeyword("  알고리즘  ")).isEqualTo("알고리즘");
    }

    @Test
    void escapeLikeKeywordEscapesBackslashPercentAndUnderscoreInOrder() {
        assertThat(LikeQueryUtils.escapeLikeKeyword("\\100%_완료"))
                .isEqualTo("\\\\100\\%\\_완료");
    }
}

package com.cotato.blankit.domain.feedback.service;

import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.task.entity.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EstimatedTimeCalculatorTest {

    private final EstimatedTimeCalculator calculator = new EstimatedTimeCalculator();

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

    private Feedback fb(int progressRate, int cumulativeElapsed, Integer consecutiveCount, Integer intervalDiff) {
        Feedback f = Feedback.create(null, null, null, progressRate, null, false);
        f.updateMetrics(0, cumulativeElapsed, consecutiveCount, intervalDiff);
        return f;
    }

    private Task task(Integer estimatedMinutes) {
        return Task.create(null, null, "test", LocalDate.now(), null, estimatedMinutes);
    }

    private Task taskWithSimilar(Integer estimatedMinutes) {
        Task similar = Task.create(null, null, "similar", LocalDate.now(), null, null);
        return Task.create(null, null, "test", LocalDate.now(), similar, estimatedMinutes);
    }

    // ─── 3-1 단순 계산 (비슷한 과업 데이터 없을 때) ──────────────────────────────

    @Test
    void progressRate100_returns0() {
        assertThat(calculator.calculate(task(null), 100, 6000L, List.of(), List.of())).isEqualTo(0);
    }

    @Test
    void calculateSimple_halfDone_returnsHalfElapsed() {
        // 3000초 사용 후 50% 진행 → 경과=50분, 남은 예상 = 50 * 50/50 = 50분
        assertThat(calculator.calculate(task(null), 50, 3000L, List.of(), List.of())).isEqualTo(50);
    }

    @Test
    void calculateSimple_quarterDone_returnsTripleElapsed() {
        // 900초 사용 후 25% 진행 → 경과=15분, 남은 예상 = 15 * 75/25 = 45분
        assertThat(calculator.calculate(task(null), 25, 900L, List.of(), List.of())).isEqualTo(45);
    }

    @Test
    void calculateSimple_ceilingApplied() {
        // 61초 사용 후 3% 진행 → 1.0167분 * 97/3 = 32.87 → ceil = 33분
        assertThat(calculator.calculate(task(null), 3, 61L, List.of(), List.of())).isEqualTo(33);
    }

    @Test
    void calculateSimple_veryEarlyStage_onePercent() {
        // 60초 사용 후 1% 진행 (극초기 경계값) → 1분 * 99/1 = 99분
        assertThat(calculator.calculate(task(null), 1, 60L, List.of(), List.of())).isEqualTo(99);
    }

    @Test
    void calculateSimple_nearlyComplete_99Percent() {
        // 5940초(99분) 사용 후 99% 진행 (거의 완료 경계값) → 99분 * 1/99 = 1.0분 → ceil = 1분
        assertThat(calculator.calculate(task(null), 99, 5940L, List.of(), List.of())).isEqualTo(1);
    }

    @Test
    void calculate_similarTaskSetButNoFeedbacks_fallsBackToSimple() {
        // similarTask 연결은 있지만 유사 과업의 피드백 목록이 비어있으면 단순 공식으로 폴백
        // 1800초 사용 후 30% 진행 → 30분 * 70/30 = 70분
        Task t = taskWithSimilar(null);
        assertThat(calculator.calculate(t, 30, 1800L, List.of(), List.of())).isEqualTo(70);
    }

    // ─── 3-4 과거 구간 시간 환산 ──────────────────────────────────────────

    @Test
    void getSimilarElapsedSeconds_progressRate0_returns0() {
        List<Feedback> similar = List.of(fb(50, 3000, null, null));
        assertThat(calculator.getSimilarElapsedSeconds(similar, 0)).isEqualTo(0);
    }

    @Test
    void getSimilarElapsedSeconds_emptyList_returns0() {
        assertThat(calculator.getSimilarElapsedSeconds(List.of(), 50)).isEqualTo(0);
    }

    @Test
    void getSimilarElapsedSeconds_interpolatesWithinInterval() {
        // [{50%, 3000초}] at 25% → 구간속도=3000/50=60초/%, 환산=round(60*25)=1500초
        List<Feedback> similar = List.of(fb(50, 3000, null, null));
        assertThat(calculator.getSimilarElapsedSeconds(similar, 25)).isEqualTo(1500);
    }

    @Test
    void getSimilarElapsedSeconds_exactMatchRate() {
        // [{50%, 3000초}] at 50% → 구간속도=60, round(60*50)=3000초 (실제 누적시간과 일치)
        List<Feedback> similar = List.of(fb(50, 3000, null, null));
        assertThat(calculator.getSimilarElapsedSeconds(similar, 50)).isEqualTo(3000);
    }

    @Test
    void getSimilarElapsedSeconds_extrapolatesAboveLastRate() {
        // [{50%, 3000초}] at 75% → 마지막 구간속도=60, round(60*75)=4500초
        List<Feedback> similar = List.of(fb(50, 3000, null, null));
        assertThat(calculator.getSimilarElapsedSeconds(similar, 75)).isEqualTo(4500);
    }

    @Test
    void getSimilarElapsedSeconds_multiplePoints_interpolatesBetween() {
        // [{30%, 1800초}, {60%, 4200초}] at 40%
        // 40%는 30~60% 구간 해당 → 1800 + (2400/30)*(40-30) = 1800+800 = 2600초
        List<Feedback> similar = List.of(fb(30, 1800, null, null), fb(60, 4200, null, null));
        assertThat(calculator.getSimilarElapsedSeconds(similar, 40)).isEqualTo(2600);
    }

    @Test
    void getSimilarElapsedSeconds_multiplePoints_exactMatchAtSecondPoint() {
        // [{30%, 1800초}, {60%, 4200초}] at 60%
        // 60%는 30~60% 구간 해당 → 1800 + (2400/30)*(60-30) = 1800+2400 = 4200초 (실제 누적과 일치)
        List<Feedback> similar = List.of(fb(30, 1800, null, null), fb(60, 4200, null, null));
        assertThat(calculator.getSimilarElapsedSeconds(similar, 60)).isEqualTo(4200);
    }

    @Test
    void getSimilarElapsedSeconds_multiplePoints_extrapolatesFromLastInterval() {
        // [{30%, 1800초}, {60%, 4200초}] at 80%
        // 80%는 모든 포인트 초과 → 4200 + (2400/30)*(80-60) = 4200+1600 = 5800초
        List<Feedback> similar = List.of(fb(30, 1800, null, null), fb(60, 4200, null, null));
        assertThat(calculator.getSimilarElapsedSeconds(similar, 80)).isEqualTo(5800);
    }

    // ─── 3-3 연속성 카운트 ──────────────────────────────────────────────

    @Test
    void calculateCount_nullPrevFeedback_returns1() {
        assertThat(calculator.calculateCount(100L, null)).isEqualTo(1);
    }

    @Test
    void calculateCount_nullIntervalDiff_returns1() {
        // intervalDiff=null → 이전 방향 데이터 없음 → count=1로 초기화
        Feedback prev = fb(50, 3000, 3, null);
        assertThat(calculator.calculateCount(100L, prev)).isEqualTo(1);
    }

    @Test
    void calculateCount_sameSignPositive_incrementsCount() {
        // 현재 B=+50, 이전 intervalDiff=+100 → 같은 부호(양수) → count+1
        Feedback prev = fb(50, 3000, 3, 100);
        assertThat(calculator.calculateCount(50L, prev)).isEqualTo(4);
    }

    @Test
    void calculateCount_sameSignNegative_incrementsCount() {
        // 현재 B=-50, 이전 intervalDiff=-100 → 같은 부호(음수) → count+1
        Feedback prev = fb(50, 3000, 2, -100);
        assertThat(calculator.calculateCount(-50L, prev)).isEqualTo(3);
    }

    @Test
    void calculateCount_differentSign_posToNeg_resetsToOne() {
        // 양수 → 음수 부호 변경 → count=1 리셋
        Feedback prev = fb(50, 3000, 5, 100);
        assertThat(calculator.calculateCount(-50L, prev)).isEqualTo(1);
    }

    @Test
    void calculateCount_differentSign_negToPos_resetsToOne() {
        // 음수 → 양수 부호 변경 → count=1 리셋
        Feedback prev = fb(50, 3000, 5, -100);
        assertThat(calculator.calculateCount(50L, prev)).isEqualTo(1);
    }

    @Test
    void calculateCount_zeroCurrent_sameSignAsPositivePrev_incrementsCount() {
        // B=0 (non-negative), 이전 intervalDiff=+100 (non-negative) → 같은 부호 → count+1
        Feedback prev = fb(50, 3000, 2, 100);
        assertThat(calculator.calculateCount(0L, prev)).isEqualTo(3);
    }

    @Test
    void calculateCount_zeroCurrent_differentSignFromNegativePrev_resetsToOne() {
        // B=0 (non-negative), 이전 intervalDiff=-100 (negative) → 부호 불일치 → count=1 리셋
        Feedback prev = fb(50, 3000, 3, -100);
        assertThat(calculator.calculateCount(0L, prev)).isEqualTo(1);
    }

    // ─── 3-2 비슷한 과업 데이터 활용 계산 ────────────────────────────────────

    @Test
    void negativeResult_returnsFallback30() {
        // task=1분(60초), similar=[{50%,9000초}], 현재 600초에 50%, 이전 없음
        // A=60-600=-540, B=600-9000=-8400, C=(12-0)-(180-0)=-168, D=0.5
        // count=1(이전 없음), factor=1/3
        // resultSec=(-540)+(-8400)+(-168)*(1/3)*0.5=-8968 → 음수 → 폴백 30분
        Task t = taskWithSimilar(1);
        List<Feedback> similar = List.of(fb(50, 9000, null, null));
        assertThat(calculator.calculate(t, 50, 600L, List.of(), similar)).isEqualTo(30);
    }

    @Test
    void calculateWithSimilar_firstFeedback_positiveResult() {
        // 첫 피드백 (이전 없음), 현재가 과거보다 빠른 경우 → 양수 결과 확인
        // task=60분, similar=[{50%,3000초}], 현재 1200초에 30%
        // A=2400, B=-600(빠름), C=-20, D=70, count=1, factor=1/3
        // resultSec = 1800 + (-20)*(1/3)*70 = 1800-466.7 = 1333.3 → ceil(22.2) = 23분
        Task t = taskWithSimilar(60);
        List<Feedback> similar = List.of(fb(50, 3000, null, null));
        assertThat(calculator.calculate(t, 30, 1200L, List.of(), similar)).isEqualTo(23);
    }

    @Test
    void calculateWithSimilar_B_exactlyZero_sameSpeedAsSimilar() {
        // 현재 구간 소요시간 == 과거 구간 소요시간 → B=0, C=0
        // task=60분, similar=[{50%,3000초}], 현재 3000초에 50%, 이전 없음
        // similarAtCurrent=round(3000/50*50)=3000, A=3600-3000=600, B=0, C=0
        // resultSec=600 → ceil(600/60)=10분
        Task t = taskWithSimilar(60);
        List<Feedback> similar = List.of(fb(50, 3000, null, null));
        assertThat(calculator.calculate(t, 50, 3000L, List.of(), similar)).isEqualTo(10);
    }

    @Test
    void progressRate100_withSimilarTask_returnsZero() {
        // 비슷한 과업 데이터가 있어도 진행률 100%이면 즉시 0 반환 (완료 상태 조기 반환)
        Task t = taskWithSimilar(60);
        List<Feedback> similar = List.of(fb(50, 3000, null, null));
        assertThat(calculator.calculate(t, 100, 6000L, List.of(), similar)).isEqualTo(0);
    }

    @Test
    void higherCount_strongerCorrection() {
        // task=70분, similar=[{50%,1575초}], 현재 8500초에 50%, A+B=3570초
        // C=120초/%p, D=50, prevLow(count=1→2, factor=0.5): C*f*D=3000 → 3570+3000=6570 → 110분
        // prevHigh(count=10→11, factor=11/13): C*f*D≈5077 → 3570+5077=8647 → 145분
        Task t = taskWithSimilar(70);
        List<Feedback> similar = List.of(fb(50, 1575, null, null));

        Feedback prevLow = fb(30, 1500, 1, 100);
        assertThat(calculator.calculate(t, 50, 8500L, List.of(prevLow), similar)).isEqualTo(110);

        Feedback prevHigh = fb(30, 1500, 10, 100);
        assertThat(calculator.calculate(t, 50, 8500L, List.of(prevHigh), similar)).isEqualTo(145);
    }

    @Test
    void slowerThanSimilar_givesHigherEstimateThanFaster() {
        // task=120분, similar=[{50%,3600초}], prev(10%,200초,count=10,diff=+100), A+B=4320초(공통)
        // 느린_경우(8000초): C=140, B=4920>0 동부호→count=11, factor=11/13, C*f*D≈5923 → 4320+5923=10243 → 171분
        // 빠른_경우(800초): C=-4, B=-2280<0 부호변경→count=1, factor=1/3, C*f*D=-66.7 → 4320-66.7=4253 → 71분
        Task t = taskWithSimilar(120);
        List<Feedback> similar = List.of(fb(50, 3600, null, null));
        Feedback prev = fb(10, 200, 10, 100);

        assertThat(calculator.calculate(t, 50, 8000L, List.of(prev), similar)).isEqualTo(171);
        assertThat(calculator.calculate(t, 50, 800L, List.of(prev), similar)).isEqualTo(71);
    }

    // ─── piecewise 단조증가 회귀 방지 ────────────────────────────────────────

    @Test
    void getSimilarElapsedSeconds_isMonotonicallyIncreasing() {
        // piecewise 구간 보간에서 경계값 급등이 없는지 검증 (버그 재발 방지)
        // [{30%,1800초},{60%,4200초}] 기준 rate=1~80 전 구간에서 단조증가해야 함
        List<Feedback> similar = List.of(fb(30, 1800, null, null), fb(60, 4200, null, null));
        long prev = 0;
        for (int rate = 1; rate <= 80; rate++) {
            long cur = calculator.getSimilarElapsedSeconds(similar, rate);
            assertThat(cur).as("rate=%d에서 단조증가 위반", rate).isGreaterThanOrEqualTo(prev);
            prev = cur;
        }
    }

    // ─── 피드백 체인 (A 항 연속성) ─────────────────────────────────────────

    @Test
    void sequenceFeedback_chainedCallsProduceConsistentEstimates() {
        // 1단계: task=60분, similar=[{50%,3000초}], 10%에 600초
        //   A=3600-600=3000, B=0, C=0 → 3000초=50분
        // 2단계: estimatedTime을 50분으로 갱신 후, 50%에 3000초
        //   A=3000-2400=600, B=0, C=0 → 600초=10분
        Task t = taskWithSimilar(60);
        List<Feedback> similar = List.of(fb(50, 3000, null, null));

        int firstResult = calculator.calculate(t, 10, 600L, List.of(), similar);
        assertThat(firstResult).isEqualTo(50);

        t.updateEstimatedTime(firstResult);
        Feedback firstFb = fb(10, 600, 1, 0);
        int secondResult = calculator.calculate(t, 50, 3000L, List.of(firstFb), similar);
        assertThat(secondResult).isEqualTo(10);
    }
}

package com.cotato.blankit.domain.feedback.service;

import com.cotato.blankit.domain.feedback.entity.Feedback;
import com.cotato.blankit.domain.task.entity.Task;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EstimatedTimeCalculator {

    private static final int FALLBACK_MINUTES = 30;

    public int calculate(Task task, int progressRate, long cumulativeElapsedSeconds,
                         List<Feedback> previousFeedbacks, List<Feedback> similarTaskFeedbacks) {
        if (progressRate == 100) {
            return 0;
        }
        boolean hasSimilarData = task.getSimilarTask() != null && !similarTaskFeedbacks.isEmpty();
        if (!hasSimilarData) {
            return calculateSimple(progressRate, cumulativeElapsedSeconds);
        }
        return calculateWithSimilar(task, progressRate, cumulativeElapsedSeconds, previousFeedbacks, similarTaskFeedbacks);
    }

    public int calculateCount(long currentB, Feedback prevFeedback) {
        if (prevFeedback == null || prevFeedback.getIntervalDiff() == null) {
            return 1;
        }
        boolean sameSign = (currentB >= 0) == (prevFeedback.getIntervalDiff() >= 0);
        return sameSign ? prevFeedback.getConsecutiveCount() + 1 : 1;
    }

    // 3-1
    private int calculateSimple(int progressRate, long cumulativeElapsedSeconds) {
        double elapsedMinutes = cumulativeElapsedSeconds / 60.0;
        double remainingMinutes = elapsedMinutes * (100 - progressRate) / progressRate;
        return toMinutesWithFallback(remainingMinutes);
    }

    // 3-2
    private int calculateWithSimilar(Task task, int currentRate, long cumulativeElapsedSeconds,
                                     List<Feedback> previousFeedbacks, List<Feedback> similarTaskFeedbacks) {
        Feedback prevFeedback = previousFeedbacks.isEmpty() ? null : previousFeedbacks.get(previousFeedbacks.size() - 1);

        int prevRate = prevFeedback != null ? prevFeedback.getProgressRate() : 0;
        long prevCumulative = prevFeedback != null ? prevFeedback.getCumulativeElapsedTime() : 0;

        long intervalElapsedCurrent = cumulativeElapsedSeconds - prevCumulative;

        long similarAtCurrent = getSimilarElapsedSeconds(similarTaskFeedbacks, currentRate);
        long similarAtPrev = getSimilarElapsedSeconds(similarTaskFeedbacks, prevRate);
        long intervalElapsedSimilar = similarAtCurrent - similarAtPrev;

        long prevEstimatedSeconds = task.getEstimatedTime() != null ? task.getEstimatedTime() * 60L : 0L;
        long A = prevEstimatedSeconds - intervalElapsedCurrent;
        long B = intervalElapsedCurrent - intervalElapsedSimilar;

        double speedCurrentAtNow = currentRate > 0 ? (double) cumulativeElapsedSeconds / currentRate : 0;
        double speedSimilarAtNow = currentRate > 0 ? (double) similarAtCurrent / currentRate : 0;
        double speedCurrentAtPrev = prevRate > 0 ? (double) prevCumulative / prevRate : 0;
        double speedSimilarAtPrev = prevRate > 0 ? (double) similarAtPrev / prevRate : 0;
        double C = (speedCurrentAtNow - speedSimilarAtNow) - (speedCurrentAtPrev - speedSimilarAtPrev);

        double D = (100 - currentRate) / 100.0;

        int count = calculateCount(B, prevFeedback);
        double correctionFactor = (double) count / (count + 2);

        double resultSeconds = A + B + C * correctionFactor * D;
        return toMinutesWithFallback(resultSeconds / 60.0);
    }

    // 3-4
    long getSimilarElapsedSeconds(List<Feedback> similarFeedbacks, int progressRate) {
        if (progressRate == 0 || similarFeedbacks.isEmpty()) {
            return 0;
        }
        int prevRate = 0;
        long prevCumulative = 0;
        for (Feedback fb : similarFeedbacks) {
            int fbRate = fb.getProgressRate();
            long fbCumulative = fb.getCumulativeElapsedTime();
            if (progressRate <= fbRate) {
                int intervalRate = fbRate - prevRate;
                if (intervalRate == 0) return 0;
                long intervalElapsed = fbCumulative - prevCumulative;
                double speedPerPercent = (double) intervalElapsed / intervalRate;
                return Math.round(speedPerPercent * progressRate);
            }
            prevRate = fbRate;
            prevCumulative = fbCumulative;
        }
        // progressRate가 비슷한 과업의 최종 진행률을 초과한 경우 → 마지막 구간 속도 적용
        Feedback lastFb = similarFeedbacks.get(similarFeedbacks.size() - 1);
        int lastPrevRate = similarFeedbacks.size() > 1
                ? similarFeedbacks.get(similarFeedbacks.size() - 2).getProgressRate() : 0;
        long lastPrevCumulative = similarFeedbacks.size() > 1
                ? similarFeedbacks.get(similarFeedbacks.size() - 2).getCumulativeElapsedTime() : 0;
        int lastIntervalRate = lastFb.getProgressRate() - lastPrevRate;
        if (lastIntervalRate == 0) return 0;
        long lastIntervalElapsed = lastFb.getCumulativeElapsedTime() - lastPrevCumulative;
        double speedPerPercent = (double) lastIntervalElapsed / lastIntervalRate;
        return Math.round(speedPerPercent * progressRate);
    }

    // 3-5
    private int toMinutesWithFallback(double minutes) {
        if (minutes < 0) {
            return FALLBACK_MINUTES;
        }
        return (int) Math.ceil(minutes);
    }
}

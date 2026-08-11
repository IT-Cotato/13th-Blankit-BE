package com.cotato.blankit.domain.recommendation.service;

import com.cotato.blankit.domain.recommendation.dto.response.AllRecommendationResponse;
import com.cotato.blankit.domain.recommendation.dto.response.RecommendationModesResponse;
import com.cotato.blankit.domain.recommendation.dto.response.RecommendedTaskItem;
import com.cotato.blankit.domain.recommendation.dto.response.TodayRecommendationResponse;
import com.cotato.blankit.domain.recommendation.dto.response.ThirtyMinutePackRecommendationResponse;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.feedback.service.FeedbackService;
import com.cotato.blankit.domain.task.entity.TaskPriority;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class RecommendationService {

    private final TaskRepository taskRepository;
    private final FeedbackService feedbackService;
    private final Clock clock;

    public TodayRecommendationResponse getTodayRecommendation(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<ScoredTask> ranked = buildRanked(userId, today);

        if (ranked.isEmpty()) {
            return new TodayRecommendationResponse(today, 0L, List.of());
        }

        long totalMinutes = calculateTotalMinutes(ranked, today);
        List<ScoredTask> top = ranked.subList(0, Math.min(3, ranked.size()));
        Map<Long, String> memoMap = getLatestMemoMap(top.stream().map(st -> st.task().getId()).toList());

        List<RecommendedTaskItem> topTasks = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            topTasks.add(toItem(top.get(i), i + 1, today, memoMap));
        }

        return new TodayRecommendationResponse(today, totalMinutes, topTasks);
    }

    public AllRecommendationResponse getAllRecommendation(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<ScoredTask> ranked = buildRanked(userId, today);

        if (ranked.isEmpty()) {
            return new AllRecommendationResponse(today, List.of());
        }

        Map<Long, String> memoMap = getLatestMemoMap(ranked.stream().map(st -> st.task().getId()).toList());
        List<RecommendedTaskItem> allTasks = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            allTasks.add(toItem(ranked.get(i), i + 1, today, memoMap));
        }

        return new AllRecommendationResponse(today, allTasks);
    }

    public RecommendationModesResponse getRecommendationModes(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<ScoredTask> ranked = buildRanked(userId, today);
        long totalMinutes = calculateTotalMinutes(ranked, today);

        List<ScoredTask> modeRanked = ranked.stream()
                .filter(st -> ChronoUnit.DAYS.between(today, st.task().getDeadline()) > 0)
                .toList();

        Map<Long, String> memoMap = getLatestMemoMap(modeRanked.stream().map(st -> st.task().getId()).toList());

        return new RecommendationModesResponse(List.of(
                buildModeItem("FIRE", "불끄기", "오늘 최소 시간을 빨간색(상) 과업에 올인하는 조합",
                        buildFireMode(modeRanked, totalMinutes, memoMap)),
                buildModeItem("BALANCE", "밸런스", "빨리 끝나는 과업으로 성취감을 먼저 얻고 빨간색 과업 진입",
                        buildBalanceMode(modeRanked, memoMap)),
                buildModeItem("TASTE", "찍먹", "각 우선순위 1등 과업을 하나씩 맛보는 조합",
                        buildTasteMode(modeRanked, memoMap)),
                buildModeItem("CLEAR", "해치우기", "마감이 가장 급한 과업부터 빠르게 끝내는 조합",
                        buildClearMode(modeRanked, totalMinutes, memoMap))
        ));
    }

    private RecommendationModesResponse.RecommendationModeItem buildModeItem(
            String mode, String modeName, String description,
            List<RecommendationModesResponse.ModeTaskItem> tasks) {
        return new RecommendationModesResponse.RecommendationModeItem(mode, modeName, description, tasks);
    }

    private List<RecommendationModesResponse.ModeTaskItem> buildFireMode(List<ScoredTask> ranked, long totalMinutes, Map<Long, String> memoMap) {
        List<ScoredTask> highTasks = ranked.stream()
                .filter(st -> st.task().getPriority() == TaskPriority.HIGH)
                .filter(st -> st.task().getEstimatedTime() != null && st.task().getEstimatedTime() > 0)
                .toList();

        List<RecommendationModesResponse.ModeTaskItem> result = new ArrayList<>();
        long remaining = totalMinutes;

        for (ScoredTask st : highTasks) {
            if (remaining <= 0) break;
            int allocated = (int) Math.min(st.task().getEstimatedTime(), remaining);
            result.add(toModeTaskItem(st.task(), allocated, memoMap));
            remaining -= allocated;
        }

        return result;
    }

    private List<RecommendationModesResponse.ModeTaskItem> buildBalanceMode(List<ScoredTask> ranked, Map<Long, String> memoMap) {
        ScoredTask quickTask = ranked.stream()
                .filter(st -> st.task().getPriority() != TaskPriority.HIGH)
                .filter(st -> st.task().getEstimatedTime() != null && st.task().getEstimatedTime() > 0)
                .min(Comparator.comparingInt(st -> st.task().getEstimatedTime()))
                .orElse(null);

        ScoredTask highTask = firstByPriority(ranked, TaskPriority.HIGH);

        if (quickTask == null || highTask == null) return List.of();

        return List.of(
                toModeTaskItem(quickTask.task(), quickTask.task().getEstimatedTime(), memoMap),
                toModeTaskItem(highTask.task(), highTask.task().getEstimatedTime(), memoMap)
        );
    }

    private List<RecommendationModesResponse.ModeTaskItem> buildTasteMode(List<ScoredTask> ranked, Map<Long, String> memoMap) {
        ScoredTask highTask = firstByPriority(ranked, TaskPriority.HIGH);
        ScoredTask medTask  = firstByPriority(ranked, TaskPriority.MEDIUM);
        ScoredTask lowTask  = firstByPriority(ranked, TaskPriority.LOW);

        int present = (highTask != null ? 1 : 0) + (medTask != null ? 1 : 0) + (lowTask != null ? 1 : 0);
        if (present < 2) return List.of();

        List<RecommendationModesResponse.ModeTaskItem> result = new ArrayList<>();
        if (highTask != null) result.add(toModeTaskItem(highTask.task(), highTask.task().getEstimatedTime(), memoMap));
        if (medTask != null)  result.add(toModeTaskItem(medTask.task(),  medTask.task().getEstimatedTime(), memoMap));
        if (lowTask != null)  result.add(toModeTaskItem(lowTask.task(),  lowTask.task().getEstimatedTime(), memoMap));
        return result;
    }

    private ScoredTask firstByPriority(List<ScoredTask> ranked, TaskPriority priority) {
        return ranked.stream()
                .filter(st -> st.task().getPriority() == priority)
                .filter(st -> st.task().getEstimatedTime() != null && st.task().getEstimatedTime() > 0)
                .findFirst()
                .orElse(null);
    }

    private List<RecommendationModesResponse.ModeTaskItem> buildClearMode(List<ScoredTask> ranked, long totalMinutes, Map<Long, String> memoMap) {
        if (totalMinutes <= 0) return List.of();

        List<ScoredTask> byEstimated = ranked.stream()
                .filter(st -> st.task().getEstimatedTime() != null && st.task().getEstimatedTime() > 0)
                .sorted(Comparator.comparingInt(st -> st.task().getEstimatedTime()))
                .toList();

        if (byEstimated.isEmpty()) return List.of();

        List<RecommendationModesResponse.ModeTaskItem> result = new ArrayList<>();
        long accumulated = 0;

        for (ScoredTask st : byEstimated) {
            int est = st.task().getEstimatedTime();
            result.add(toModeTaskItem(st.task(), est, memoMap));
            accumulated += est;
            if (accumulated >= totalMinutes) break;
        }

        return result;
    }

    private RecommendationModesResponse.ModeTaskItem toModeTaskItem(Task task, Integer recommendedMinutes, Map<Long, String> memoMap) {
        return new RecommendationModesResponse.ModeTaskItem(
                task.getId(),
                task.getTitle(),
                task.getPriority(),
                task.getCategory().getColor(),
                task.getCategory().getIconKey(),
                recommendedMinutes,
                task.getProgressRate(),
                memoMap.get(task.getId())
        );
    }

    @Transactional(readOnly = true)
    public ThirtyMinutePackRecommendationResponse getThirtyMinutePackRecommendation(
            Long userId, int availableMinutes
    ) {
        if (availableMinutes != 30) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        LocalDate today = LocalDate.now(clock);
        List<ThirtyMinutePackRecommendationResponse.TaskItem> tasks =
                taskRepository.findActiveTasksForRecommendation(userId, today).stream()
                        .filter(task -> task.getEstimatedTime() != null && task.getEstimatedTime() > 0)
                        .filter(task -> progress(task) < 100)
                        .map(task -> toThirtyMinutePackItem(task, availableMinutes))
                        .sorted(Comparator
                                .comparing(ThirtyMinutePackRecommendationResponse.TaskItem::progressPerMinute)
                                .reversed()
                                .thenComparing(ThirtyMinutePackRecommendationResponse.TaskItem::taskId))
                        .limit(3)
                        .toList();
        return new ThirtyMinutePackRecommendationResponse(availableMinutes, tasks);
    }

    private ThirtyMinutePackRecommendationResponse.TaskItem toThirtyMinutePackItem(
            Task task, int availableMinutes
    ) {
        int currentProgress = progress(task);
        int remainingProgress = 100 - currentProgress;
        BigDecimal progressPerMinute = BigDecimal.valueOf(remainingProgress)
                .divide(BigDecimal.valueOf(task.getEstimatedTime()), 4, RoundingMode.HALF_UP);
        BigDecimal expectedIncrease = progressPerMinute.multiply(BigDecimal.valueOf(availableMinutes))
                .min(BigDecimal.valueOf(remainingProgress))
                .setScale(2, RoundingMode.HALF_UP);
        return new ThirtyMinutePackRecommendationResponse.TaskItem(
                task.getId(), task.getTitle(), task.getCategory().getColor(), task.getCategory().getIconKey(),
                currentProgress, task.getEstimatedTime(), progressPerMinute, expectedIncrease);
    }

    private int progress(Task task) {
        return task.getProgressRate() == null ? 0 : task.getProgressRate();
    }

    private List<ScoredTask> buildRanked(Long userId, LocalDate today) {
        List<Task> tasks = taskRepository.findActiveTasksForRecommendation(userId, today);
        if (tasks.isEmpty()) return List.of();
        List<ScoredTask> ranked = rankTasks(tasks, today);
        assignPriorities(ranked);
        return ranked;
    }

    private long calculateTotalMinutes(List<ScoredTask> ranked, LocalDate today) {
        return Math.round(
                ranked.stream()
                        .filter(st -> st.task().getEstimatedTime() != null
                                && ChronoUnit.DAYS.between(today, st.task().getDeadline()) > 0)
                        .mapToDouble(st -> (double) st.task().getEstimatedTime()
                                / ChronoUnit.DAYS.between(today, st.task().getDeadline()))
                        .sum()
        );
    }

    private List<ScoredTask> rankTasks(List<Task> tasks, LocalDate today) {
        Comparator<Task> urgencyKey = Comparator
                .comparingLong((Task t) -> ChronoUnit.DAYS.between(today, t.getDeadline()))
                .thenComparingInt(t -> t.isStarred() ? 0 : 1);

        Comparator<Task> progressKey = Comparator
                .comparingInt((Task t) -> t.getProgressRate() == null ? 0 : t.getProgressRate());

        Map<Long, Integer> urgencyRank = toRankMap(tasks, urgencyKey);
        Map<Long, Integer> progressRank = toRankMap(tasks, progressKey);

        return tasks.stream()
                .map(t -> {
                    int u = urgencyRank.get(t.getId());
                    int p = progressRank.get(t.getId());
                    BigDecimal score = BigDecimal.valueOf(u * 0.8 + p * 0.2)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new ScoredTask(t, score);
                })
                .sorted(Comparator.comparing(ScoredTask::score).thenComparing(st -> st.task().getCreatedAt()))
                .toList();
    }

    private Map<Long, Integer> toRankMap(List<Task> tasks, Comparator<Task> keyComparator) {
        List<Task> ordered = new ArrayList<>(tasks);
        ordered.sort(keyComparator);
        Map<Long, Integer> map = new HashMap<>();
        int rank = 1;
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0 && keyComparator.compare(ordered.get(i), ordered.get(i - 1)) != 0) {
                rank++;
            }
            map.put(ordered.get(i).getId(), rank);
        }
        return map;
    }

    private void assignPriorities(List<ScoredTask> ranked) {
        int n = ranked.size();
        int highEnd   = (n == 1) ? 1 : (n == 2) ? 1 : (int) Math.round(n * 0.3);
        int mediumEnd = (n == 1) ? 1 : (n == 2) ? 2 : (int) Math.round(n * 0.7);

        for (int i = 0; i < ranked.size(); i++) {
            int rank = i + 1;
            TaskPriority priority = (rank <= highEnd)   ? TaskPriority.HIGH
                                  : (rank <= mediumEnd) ? TaskPriority.MEDIUM
                                                        : TaskPriority.LOW;
            ranked.get(i).task().updatePriority(priority);
        }
    }

    private RecommendedTaskItem toItem(ScoredTask st, int rankOrder, LocalDate today, Map<Long, String> memoMap) {
        Task t = st.task();
        long days = ChronoUnit.DAYS.between(today, t.getDeadline());
        Integer recommendedMinutes = (t.getEstimatedTime() == null || days <= 0)
                ? null
                : (int) Math.round((double) t.getEstimatedTime() / days);

        return new RecommendedTaskItem(
                t.getId(),
                t.getTitle(),
                t.getPriority(),
                t.getCategory().getColor(),
                t.getCategory().getIconKey(),
                rankOrder,
                st.score(),
                recommendedMinutes,
                t.getProgressRate(),
                memoMap.get(t.getId())
        );
    }

    private Map<Long, String> getLatestMemoMap(List<Long> taskIds) {
        return feedbackService.getLatestMemoMap(taskIds);
    }

    private record ScoredTask(Task task, BigDecimal score) {}
}

package com.cotato.blankit.domain.recommendation.service;

import com.cotato.blankit.domain.recommendation.dto.response.AllRecommendationResponse;
import com.cotato.blankit.domain.recommendation.dto.response.RecommendedTaskItem;
import com.cotato.blankit.domain.recommendation.dto.response.TodayRecommendationResponse;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskPriority;
import com.cotato.blankit.domain.task.repository.TaskRepository;
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
    private final Clock clock;

    public TodayRecommendationResponse getTodayRecommendation(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<ScoredTask> ranked = buildRanked(userId, today);

        if (ranked.isEmpty()) {
            return new TodayRecommendationResponse(today, 0L, List.of());
        }

        long totalMinutes = Math.round(
                filterForTimeCalculation(ranked, today).stream()
                        .mapToDouble(st -> (double) st.task().getEstimatedTime() / ChronoUnit.DAYS.between(today, st.task().getDeadline()))
                        .sum()
        );

        List<RecommendedTaskItem> topTasks = new ArrayList<>();
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            topTasks.add(toItem(ranked.get(i), i + 1, today));
        }

        return new TodayRecommendationResponse(today, totalMinutes, topTasks);
    }

    public AllRecommendationResponse getAllRecommendation(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<ScoredTask> ranked = buildRanked(userId, today);

        if (ranked.isEmpty()) {
            return new AllRecommendationResponse(today, List.of());
        }

        List<RecommendedTaskItem> allTasks = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            allTasks.add(toItem(ranked.get(i), i + 1, today));
        }

        return new AllRecommendationResponse(today, allTasks);
    }

    private List<ScoredTask> buildRanked(Long userId, LocalDate today) {
        List<Task> tasks = taskRepository.findActiveTasksForRecommendation(userId, today);
        if (tasks.isEmpty()) return List.of();
        List<ScoredTask> ranked = rankTasks(tasks, today);
        assignPriorities(ranked);
        return ranked;
    }

    private List<ScoredTask> filterForTimeCalculation(List<ScoredTask> ranked, LocalDate today) {
        return ranked.stream()
                .filter(st -> st.task().getEstimatedTime() != null
                        && ChronoUnit.DAYS.between(today, st.task().getDeadline()) > 0)
                .toList();
    }

    private List<ScoredTask> rankTasks(List<Task> tasks, LocalDate today) {
        Comparator<Task> urgencyKey = Comparator
                .comparingLong((Task t) -> ChronoUnit.DAYS.between(today, t.getDeadline()))
                .thenComparingInt(t -> t.isStarred() ? 0 : 1);

        List<Task> byUrgency = new ArrayList<>(tasks);
        byUrgency.sort(urgencyKey);
        Map<Long, Integer> urgencyRank = toRankMap(byUrgency, urgencyKey);

        Comparator<Task> progressKey = Comparator
                .comparingInt((Task t) -> t.getProgressRate() == null ? 0 : t.getProgressRate());

        List<Task> byProgress = new ArrayList<>(tasks);
        byProgress.sort(progressKey);
        Map<Long, Integer> progressRank = toRankMap(byProgress, progressKey);

        return tasks.stream()
                .map(t -> {
                    int u = urgencyRank.get(t.getId());
                    int p = progressRank.get(t.getId());
                    BigDecimal score = BigDecimal.valueOf(u * 0.8 + p * 0.2)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new ScoredTask(t, score);
                })
                .sorted(Comparator.comparing(ScoredTask::score).thenComparingLong(st -> st.task().getId()))
                .toList();
    }

    private Map<Long, Integer> toRankMap(List<Task> ordered, Comparator<Task> keyComparator) {
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

    private RecommendedTaskItem toItem(ScoredTask st, int rankOrder, LocalDate today) {
        Task t = st.task();
        long days = ChronoUnit.DAYS.between(today, t.getDeadline());
        Integer recommendedMinutes = (t.getEstimatedTime() == null || days == 0)
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
                recommendedMinutes
        );
    }

    private record ScoredTask(Task task, BigDecimal score) {}
}

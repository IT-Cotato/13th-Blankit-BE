package com.cotato.blankit.domain.recommendation.service;

import com.cotato.blankit.domain.recommendation.dto.response.AllRecommendationResponse;
import com.cotato.blankit.domain.recommendation.dto.response.RecommendationModesResponse;
import com.cotato.blankit.domain.recommendation.dto.response.RecommendedTaskItem;
import com.cotato.blankit.domain.recommendation.dto.response.TodayRecommendationResponse;
import com.cotato.blankit.domain.recommendation.dto.response.ThirtyMinutePackRecommendationResponse;
import com.cotato.blankit.domain.recommendation.entity.DailyRecommendation;
import com.cotato.blankit.domain.recommendation.entity.DailyRecommendationItem;
import com.cotato.blankit.domain.recommendation.repository.DailyRecommendationItemRepository;
import com.cotato.blankit.domain.recommendation.repository.DailyRecommendationRepository;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.feedback.service.FeedbackService;
import com.cotato.blankit.domain.task.entity.TaskPriority;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class RecommendationService {

    private final TaskRepository taskRepository;
    private final FeedbackService feedbackService;
    private final Clock clock;
    private final DailyRecommendationRepository dailyRecommendationRepository;
    private final DailyRecommendationItemRepository dailyRecommendationItemRepository;
    private final UserRepository userRepository;

    public TodayRecommendationResponse getTodayRecommendation(Long userId) {
        LocalDate today = LocalDate.now(clock);

        List<ScoredTask> ranked = buildRanked(userId, today);
        long totalMinutes = resolveTotalMinutes(userId, today, ranked);

        List<ScoredTask> top = ranked.subList(0, Math.min(3, ranked.size()));
        Map<Long, String> memoMap = getLatestMemoMap(top.stream().map(st -> st.task().getId()).toList());

        List<RecommendedTaskItem> topTasks = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            topTasks.add(toItem(top.get(i), i + 1, today, memoMap));
        }

        return new TodayRecommendationResponse(today, totalMinutes, topTasks);
    }

    private long resolveTotalMinutes(Long userId, LocalDate today, List<ScoredTask> ranked) {
        return dailyRecommendationRepository
                .findByUser_IdAndRecommendedDateAndMode(userId, today, "TODAY")
                .map(dr -> (long) dr.getTotalRecommendedMinutes())
                .orElseGet(() -> saveAndReturnTotalMinutes(userId, today, ranked));
    }

    private long saveAndReturnTotalMinutes(Long userId, LocalDate today, List<ScoredTask> ranked) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return dailyRecommendationRepository
                .findByUser_IdAndRecommendedDateAndMode(userId, today, "TODAY")
                .map(dr -> (long) dr.getTotalRecommendedMinutes())
                .orElseGet(() -> {
                    long totalMinutes = ranked.isEmpty() ? 0L : calculateTotalMinutes(ranked, today);
                    User user = userRepository.getReferenceById(userId);
                    dailyRecommendationRepository.save(DailyRecommendation.ofToday(user, today, (int) totalMinutes));
                    return totalMinutes;
                });
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

    private static final List<String> ALL_MODES = List.of("FIRE", "BALANCE", "TASTE", "CLEAR");

    public RecommendationModesResponse getRecommendationModes(Long userId) {
        LocalDate today = LocalDate.now(clock);

        if (dailyRecommendationRepository.countByUser_IdAndRecommendedDateAndModeIn(userId, today, ALL_MODES) == 4) {
            return buildModesFromCache(userId, today);
        }

        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        long count = dailyRecommendationRepository.countByUser_IdAndRecommendedDateAndModeIn(userId, today, ALL_MODES);
        if (count == 4) {
            return buildModesFromCache(userId, today);
        }

        if (count > 0) {
            deletePartialModes(userId, today);
        }

        List<ScoredTask> ranked = buildRanked(userId, today);
        long totalMinutes = calculateTotalMinutes(ranked, today);

        List<ScoredTask> modeRanked = ranked.stream()
                .filter(st -> ChronoUnit.DAYS.between(today, st.task().getDeadline()) > 0)
                .toList();

        Map<Long, String> memoMap = getLatestMemoMap(modeRanked.stream().map(st -> st.task().getId()).toList());

        List<RecommendationModesResponse.ModeTaskItem> fireTasks = buildFireMode(modeRanked, totalMinutes, memoMap);
        List<RecommendationModesResponse.ModeTaskItem> balanceTasks = buildBalanceMode(modeRanked, memoMap);
        List<RecommendationModesResponse.ModeTaskItem> tasteTasks = buildTasteMode(modeRanked, memoMap);
        List<RecommendationModesResponse.ModeTaskItem> clearTasks = buildClearMode(modeRanked, totalMinutes, memoMap);

        User user = userRepository.getReferenceById(userId);
        saveModeItems(user, today, "FIRE", fireTasks);
        saveModeItems(user, today, "BALANCE", balanceTasks);
        saveModeItems(user, today, "TASTE", tasteTasks);
        saveModeItems(user, today, "CLEAR", clearTasks);

        return new RecommendationModesResponse(List.of(
                buildModeItem("FIRE", "불끄기", "오늘 최소 시간을 빨간색(상) 과업에 올인하는 조합", fireTasks),
                buildModeItem("BALANCE", "밸런스", "빨리 끝나는 과업으로 성취감을 먼저 얻고 빨간색 과업 진입", balanceTasks),
                buildModeItem("TASTE", "찍먹", "각 우선순위 1등 과업을 하나씩 맛보는 조합", tasteTasks),
                buildModeItem("CLEAR", "해치우기", "마감이 가장 급한 과업부터 빠르게 끝내는 조합", clearTasks)
        ));
    }

    private void deletePartialModes(Long userId, LocalDate today) {
        List<DailyRecommendation> partial = dailyRecommendationRepository
                .findAllByUser_IdAndRecommendedDateAndModeIn(userId, today, ALL_MODES);
        dailyRecommendationItemRepository.deleteAllByDailyRecommendationIn(partial);
        dailyRecommendationRepository.deleteAll(partial);
    }

    private void saveModeItems(User user, LocalDate today, String mode,
            List<RecommendationModesResponse.ModeTaskItem> tasks) {
        DailyRecommendation dailyRecommendation = DailyRecommendation.ofMode(user, today, mode);
        dailyRecommendationRepository.save(dailyRecommendation);

        for (int i = 0; i < tasks.size(); i++) {
            RecommendationModesResponse.ModeTaskItem modeTask = tasks.get(i);
            Task task = taskRepository.getReferenceById(modeTask.taskId());
            dailyRecommendationItemRepository.save(
                    DailyRecommendationItem.of(dailyRecommendation, task, i + 1, null, modeTask.recommendedMinutes())
            );
        }
    }

    private RecommendationModesResponse buildModesFromCache(Long userId, LocalDate today) {
        return new RecommendationModesResponse(List.of(
                loadModeFromCache(userId, today, "FIRE", "불끄기", "오늘 최소 시간을 빨간색(상) 과업에 올인하는 조합"),
                loadModeFromCache(userId, today, "BALANCE", "밸런스", "빨리 끝나는 과업으로 성취감을 먼저 얻고 빨간색 과업 진입"),
                loadModeFromCache(userId, today, "TASTE", "찍먹", "각 우선순위 1등 과업을 하나씩 맛보는 조합"),
                loadModeFromCache(userId, today, "CLEAR", "해치우기", "마감이 가장 급한 과업부터 빠르게 끝내는 조합")
        ));
    }

    private RecommendationModesResponse.RecommendationModeItem loadModeFromCache(
            Long userId, LocalDate today, String mode, String modeName, String description) {
        DailyRecommendation cached = dailyRecommendationRepository
                .findByUser_IdAndRecommendedDateAndMode(userId, today, mode)
                .orElseThrow();

        List<DailyRecommendationItem> items = dailyRecommendationItemRepository
                .findAllByDailyRecommendationOrderByRankOrder(cached);

        List<Long> taskIds = items.stream().map(item -> item.getTask().getId()).toList();
        Map<Long, String> memoMap = getLatestMemoMap(taskIds);

        List<RecommendationModesResponse.ModeTaskItem> tasks = items.stream()
                .map(item -> {
                    Task task = item.getTask();
                    return new RecommendationModesResponse.ModeTaskItem(
                            task.getId(),
                            task.getTitle(),
                            task.getPriority(),
                            task.getCategory().getColor(),
                            task.getCategory().getIconKey(),
                            item.getRecommendedMinutes(),
                            task.getProgressRate(),
                            memoMap.get(task.getId())
                    );
                })
                .toList();

        return buildModeItem(mode, modeName, description, tasks);
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
        if (availableMinutes < 10 || availableMinutes > 30) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        LocalDate today = LocalDate.now(clock);
        List<Task> candidates = taskRepository.findActiveTasksForRecommendation(userId, today).stream()
                .filter(task -> task.getEstimatedTime() != null && task.getEstimatedTime() > 0)
                .filter(task -> progress(task) < 100)
                .toList();
        Map<Long, String> memoMap = getLatestMemoMap(candidates.stream().map(Task::getId).toList());
        List<ThirtyMinutePackRecommendationResponse.TaskItem> tasks =
                candidates.stream()
                        .map(task -> toThirtyMinutePackItem(task, availableMinutes, memoMap))
                        .sorted(Comparator
                                .comparing(ThirtyMinutePackRecommendationResponse.TaskItem::progressPerMinute)
                                .reversed()
                                .thenComparing(ThirtyMinutePackRecommendationResponse.TaskItem::taskId))
                        .limit(3)
                        .toList();
        return new ThirtyMinutePackRecommendationResponse(availableMinutes, tasks);
    }

    private ThirtyMinutePackRecommendationResponse.TaskItem toThirtyMinutePackItem(
            Task task, int availableMinutes, Map<Long, String> memoMap
    ) {
        int currentProgress = progress(task);
        int remainingProgress = 100 - currentProgress;
        BigDecimal progressPerMinute = BigDecimal.valueOf(remainingProgress)
                .divide(BigDecimal.valueOf(task.getEstimatedTime()), 4, RoundingMode.HALF_UP);
        BigDecimal expectedIncrease = progressPerMinute.multiply(BigDecimal.valueOf(availableMinutes))
                .min(BigDecimal.valueOf(remainingProgress))
                .setScale(2, RoundingMode.HALF_UP);
        return new ThirtyMinutePackRecommendationResponse.TaskItem(
                task.getId(), task.getTitle(), task.getCategory().getName(), task.getCategory().getColor(),
                task.getCategory().getIconKey(), currentProgress, task.getEstimatedTime(), progressPerMinute,
                expectedIncrease, memoMap.get(task.getId()));
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

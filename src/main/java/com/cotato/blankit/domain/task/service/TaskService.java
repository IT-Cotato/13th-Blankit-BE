package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.category.dto.response.CategoryResponse;
import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.service.CategoryService;
import com.cotato.blankit.domain.task.dto.request.RepeatRuleRequest;
import com.cotato.blankit.domain.task.dto.request.TaskCreateRequest;
import com.cotato.blankit.domain.task.dto.request.TaskUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.ReminderRangeResponse;
import com.cotato.blankit.domain.task.dto.response.TaskDetailResponse;
import com.cotato.blankit.domain.task.dto.response.TaskFormOptionsResponse;
import com.cotato.blankit.domain.task.dto.response.TaskHistoryResponse;
import com.cotato.blankit.domain.task.dto.response.TaskListResponse;
import com.cotato.blankit.domain.task.entity.NotificationSetting;
import com.cotato.blankit.domain.task.entity.NotifyBeforeOption;
import com.cotato.blankit.domain.task.entity.RepeatMonthDays;
import com.cotato.blankit.domain.task.entity.RepeatRule;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.repository.NotificationSettingRepository;
import com.cotato.blankit.domain.task.repository.RepeatRuleRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.feedback.repository.TaskSessionRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import com.cotato.blankit.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    public static final int DEFAULT_NOTIFY_BEFORE = 1440;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private final TaskRepository taskRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final RepeatRuleRepository repeatRuleRepository;
    private final TaskSessionRepository taskSessionRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final RepeatDeadlineCalculator repeatDeadlineCalculator;
    private final Clock clock;

    @Transactional
    public TaskFormOptionsResponse getFormOptions(Long userId) {
        categoryService.createDefaultCategoriesIfNeverInitialized(getUser(userId));
        List<Category> categories = categoryService.getActiveCategories(userId);
        return new TaskFormOptionsResponse(
                categories.isEmpty() ? null : categories.get(0).getId(),
                DEFAULT_NOTIFY_BEFORE,
                false,
                categories.stream().map(CategoryResponse::from).toList(),
                new ReminderRangeResponse(NotifyBeforeOption.TEN_MINUTES.getMinutes(), NotifyBeforeOption.ONE_WEEK.getMinutes()),
                NotifyBeforeOption.minutesValues()
        );
    }

    @Transactional
    public TaskDetailResponse createTask(Long userId, TaskCreateRequest request) {
        validateTitle(request.title());
        User user = getUserForUpdate(userId);
        Category category = resolveCategory(userId, request.categoryId());
        Task similarTask = resolveSimilarTaskForCreate(userId, request.similarTaskId());
        Integer estimatedTime = resolveEstimatedTime(userId, similarTask, request.estimatedTime());
        RepeatDeadlineCalculator.RepeatRuleData repeatRuleData = request.repeatRule() == null
                ? null
                : validateAndNormalizeRepeatRule(request.repeatRule());
        LocalDate deadline = resolveCreateDeadline(request.deadline(), repeatRuleData);

        Task task = taskRepository.save(Task.create(
                user,
                category,
                request.title().trim(),
                deadline,
                similarTask,
                estimatedTime
        ));

        NotificationSetting notificationSetting = notificationSettingRepository.save(NotificationSetting.create(
                task,
                resolveNotifyBefore(request.notifyBefore()),
                request.notificationEnabled() == null || request.notificationEnabled()
        ));
        RepeatRule repeatRule = repeatRuleData == null ? null : repeatRuleRepository.save(toRepeatRule(task, repeatRuleData));

        return TaskDetailResponse.from(task, notificationSetting, repeatRule, 0L);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskListResponse> getTasks(
            Long userId,
            LocalDate date,
            TaskStatus status,
            Long categoryId,
            String keyword,
            int page,
            int size
    ) {
        Page<Task> taskPage = taskRepository.searchTaskCandidates(
                userId,
                date,
                status,
                categoryId,
                normalizeKeyword(keyword),
                createTaskPageable(page, size)
        );
        LocalDate today = LocalDate.now(clock);
        return PageResponse.of(
                taskPage.getContent().stream().map(task -> TaskListResponse.from(task, today)).toList(),
                taskPage.getNumber(),
                taskPage.getSize(),
                taskPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse getTask(Long userId, Long taskId) {
        Task task = getTaskByUser(taskId, userId);
        return toDetailResponse(userId, task);
    }

    @Transactional
    public TaskDetailResponse updateTask(Long userId, Long taskId, TaskUpdateRequest request) {
        boolean changesSimilarTask = request.similarTaskId() != null || Boolean.TRUE.equals(request.clearSimilarTask());
        if (changesSimilarTask) {
            getUserForUpdate(userId);
        }
        Task task = getTaskByUser(taskId, userId);
        RepeatRule existingRepeatRule = repeatRuleRepository.findByTaskId(task.getId()).orElse(null);

        if (request.title() != null) {
            validateTitle(request.title());
            task.updateTitle(request.title().trim());
        }
        if (request.categoryId() != null) {
            task.updateCategory(categoryService.getActiveCategory(userId, request.categoryId()));
        }
        if (request.status() != null) {
            task.updateStatus(request.status());
        }
        if (request.starred() != null) {
            task.updateStarred(request.starred());
        }
        updateNotificationSetting(task, request);
        updateDeadlineAndRepeatRule(task, request, existingRepeatRule);
        updateSimilarTask(userId, task, request);

        return toDetailResponse(userId, task);
    }

    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        Task task = getTaskByUser(taskId, userId);
        taskRepository.clearSimilarTaskBySimilarTaskIdAndUserId(task.getId(), userId);
        taskRepository.clearSourceTaskBySourceTaskIdAndUserId(task.getId(), userId);
        taskSessionRepository.deleteByTaskId(task.getId());
        repeatRuleRepository.deleteByTaskId(task.getId());
        notificationSettingRepository.findByTaskId(task.getId()).ifPresent(notificationSettingRepository::delete);
        taskRepository.deleteById(task.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskHistoryResponse> getHistory(
            Long userId,
            String keyword,
            Long categoryId,
            int page,
            int size
    ) {
        Page<Task> history = taskRepository.searchHistory(
                userId,
                normalizeKeyword(keyword),
                categoryId,
                createPageable(page, size)
        );
        Map<Long, Long> elapsedTimes = getElapsedTimeMap(userId, history.getContent());
        return PageResponse.of(
                history.getContent().stream()
                        .map(task -> TaskHistoryResponse.from(task, elapsedTimes.getOrDefault(task.getId(), 0L)))
                        .toList(),
                history.getNumber(),
                history.getSize(),
                history.getTotalElements()
        );
    }

    private void updateNotificationSetting(Task task, TaskUpdateRequest request) {
        if (request.notifyBefore() == null && request.notificationEnabled() == null) {
            return;
        }
        NotificationSetting setting = getNotificationSetting(task);
        Integer notifyBefore = request.notifyBefore() == null ? setting.getNotifyBefore() : resolveNotifyBefore(request.notifyBefore());
        boolean enabled = request.notificationEnabled() == null ? setting.isEnabled() : request.notificationEnabled();
        setting.update(notifyBefore, enabled);
    }

    private void updateDeadlineAndRepeatRule(Task task, TaskUpdateRequest request, RepeatRule existingRepeatRule) {
        if (task.getSourceTask() != null && (request.repeatRule() != null || Boolean.TRUE.equals(request.clearRepeatRule()))) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE);
        }
        if (Boolean.TRUE.equals(request.clearRepeatRule())) {
            if (existingRepeatRule != null && request.deadline() == null) {
                throw new CustomException(ErrorCode.INVALID_DUE_DATE);
            }
            if (request.deadline() != null) {
                validateDeadline(request.deadline());
                task.updateDeadline(request.deadline());
            }
            repeatRuleRepository.deleteByTaskId(task.getId());
            return;
        }
        if (request.repeatRule() != null) {
            RepeatDeadlineCalculator.RepeatRuleData data = validateAndNormalizeRepeatRule(request.repeatRule());
            LocalDate nextDeadline = calculateRepeatDeadline(data);
            repeatRuleRepository.findByTaskId(task.getId())
                    .ifPresentOrElse(
                            repeatRule -> repeatRule.update(
                                    data.frequency(),
                                    data.daysOfWeek(),
                                    data.daysOfMonth(),
                                    data.monthOfYear(),
                                    data.startDate(),
                                    data.endDate()
                            ),
                            () -> repeatRuleRepository.save(toRepeatRule(task, data))
                    );
            task.updateDeadline(nextDeadline);
            return;
        }
        if (request.deadline() != null) {
            validateDeadline(request.deadline());
            if (existingRepeatRule != null) {
                throw new CustomException(ErrorCode.INVALID_RECURRENCE);
            }
            task.updateDeadline(request.deadline());
        }
    }

    private void updateSimilarTask(Long userId, Task task, TaskUpdateRequest request) {
        boolean clearSimilarTask = Boolean.TRUE.equals(request.clearSimilarTask());
        if (clearSimilarTask && request.similarTaskId() != null) {
            throw new CustomException(ErrorCode.INVALID_SIMILAR_TASK);
        }
        if (clearSimilarTask) {
            task.clearSimilarTask();
            return;
        }
        if (request.similarTaskId() != null) {
            if (task.getId().equals(request.similarTaskId())) {
                throw new CustomException(ErrorCode.SELF_SIMILAR_TASK_NOT_ALLOWED);
            }
            Task similarTask = resolveSimilarTaskForUpdate(userId, task, request.similarTaskId());
            task.updateSimilarTask(similarTask);
            task.updateEstimatedTime(resolveEstimatedTime(userId, similarTask, null));
        }
    }

    private Category resolveCategory(Long userId, Long categoryId) {
        if (categoryId != null) {
            return categoryService.getActiveCategory(userId, categoryId);
        }
        List<Category> activeCategories = categoryService.getActiveCategories(userId);
        if (activeCategories.isEmpty()) {
            throw new CustomException(ErrorCode.CATEGORY_REQUIRED);
        }
        return activeCategories.get(0);
    }

    private Task resolveSimilarTaskForCreate(Long userId, Long similarTaskId) {
        if (similarTaskId == null) {
            return null;
        }
        Task similarTask = taskRepository.findByIdAndUserId(similarTaskId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_SIMILAR_TASK));
        validateSimilarTaskDone(similarTask);
        return similarTask;
    }

    private Integer resolveEstimatedTime(Long userId, Task similarTask, Integer requestedEstimatedTime) {
        if (similarTask == null) {
            return requestedEstimatedTime;
        }
        long elapsedSeconds = taskSessionRepository.sumElapsedTimeByTaskIdAndUserId(similarTask.getId(), userId);
        return Math.toIntExact((elapsedSeconds + 59) / 60);
    }

    private Task resolveSimilarTaskForUpdate(Long userId, Task task, Long similarTaskId) {
        Task similarTask = taskRepository.findByIdAndUserId(similarTaskId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_SIMILAR_TASK));
        validateSimilarTaskDone(similarTask);
        validateNoSimilarCycle(task, similarTask);
        return similarTask;
    }

    private void validateSimilarTaskDone(Task similarTask) {
        if (similarTask.getStatus() != TaskStatus.DONE) {
            throw new CustomException(ErrorCode.SIMILAR_TASK_NOT_DONE);
        }
    }

    private void validateNoSimilarCycle(Task task, Task similarTask) {
        Task cursor = similarTask;
        Set<Long> visitedTaskIds = new HashSet<>();
        while (cursor != null) {
            if (!visitedTaskIds.add(cursor.getId())) {
                throw new CustomException(ErrorCode.CYCLIC_SIMILAR_TASK_NOT_ALLOWED);
            }
            if (task.getId().equals(cursor.getId())) {
                throw new CustomException(ErrorCode.CYCLIC_SIMILAR_TASK_NOT_ALLOWED);
            }
            cursor = cursor.getSimilarTask();
        }
    }

    private RepeatRule toRepeatRule(Task task, RepeatDeadlineCalculator.RepeatRuleData data) {
        return RepeatRule.create(
                task,
                data.frequency(),
                data.daysOfWeek(),
                data.daysOfMonth(),
                data.monthOfYear(),
                data.startDate(),
                data.endDate()
        );
    }

    private RepeatDeadlineCalculator.RepeatRuleData validateAndNormalizeRepeatRule(RepeatRuleRequest request) {
        if (request == null || request.frequency() == null) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE);
        }
        if (request.startDate() == null) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE_DATE_RANGE);
        }
        validateRepeatDateRange(request.startDate(), request.endDate());
        return switch (request.frequency()) {
            case WEEKLY -> validateWeekly(request);
            case MONTHLY -> validateMonthly(request);
            case YEARLY -> validateYearly(request);
        };
    }

    private void validateRepeatDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE_DATE_RANGE);
        }
    }

    private RepeatDeadlineCalculator.RepeatRuleData validateWeekly(RepeatRuleRequest request) {
        List<Integer> daysOfWeek = toSortedDistinctList(request.daysOfWeek());
        if (daysOfWeek.isEmpty() || daysOfWeek.stream().anyMatch(day -> day < 0 || day > 6)) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE);
        }
        rejectRelatedFields(request.daysOfMonth(), request.lastDayOfMonth(), request.monthOfYear());
        return new RepeatDeadlineCalculator.RepeatRuleData(request.frequency(), daysOfWeek, RepeatMonthDays.none(), null, request.startDate(), request.endDate());
    }

    private RepeatDeadlineCalculator.RepeatRuleData validateMonthly(RepeatRuleRequest request) {
        List<Integer> daysOfMonth = toSortedDistinctList(request.daysOfMonth());
        boolean lastDayOfMonth = Boolean.TRUE.equals(request.lastDayOfMonth());
        if (daysOfMonth.isEmpty() && !lastDayOfMonth) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE);
        }
        if (daysOfMonth.stream().anyMatch(day -> day < 1 || day > 31)
                || request.monthOfYear() != null
                || request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE);
        }
        return new RepeatDeadlineCalculator.RepeatRuleData(
                request.frequency(),
                List.of(),
                new RepeatMonthDays(daysOfMonth, lastDayOfMonth),
                null,
                request.startDate(),
                request.endDate()
        );
    }

    private RepeatDeadlineCalculator.RepeatRuleData validateYearly(RepeatRuleRequest request) {
        List<Integer> daysOfMonth = toSortedDistinctList(request.daysOfMonth());
        boolean lastDayOfMonth = Boolean.TRUE.equals(request.lastDayOfMonth());
        if (request.monthOfYear() == null
                || request.monthOfYear() < 1
                || request.monthOfYear() > 12
                || daysOfMonth.isEmpty() && !lastDayOfMonth) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE);
        }
        if (daysOfMonth.stream().anyMatch(day -> day < 1 || day > 31)
                || request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE);
        }
        return new RepeatDeadlineCalculator.RepeatRuleData(
                request.frequency(),
                List.of(),
                new RepeatMonthDays(daysOfMonth, lastDayOfMonth),
                request.monthOfYear(),
                request.startDate(),
                request.endDate()
        );
    }

    private void rejectRelatedFields(List<Integer> daysOfMonth, Boolean lastDayOfMonth, Integer monthOfYear) {
        if ((daysOfMonth != null && !daysOfMonth.isEmpty()) || Boolean.TRUE.equals(lastDayOfMonth) || monthOfYear != null) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE);
        }
    }

    private List<Integer> toSortedDistinctList(Collection<Integer> values) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new CustomException(ErrorCode.INVALID_RECURRENCE);
        }
        return new LinkedHashSet<>(values).stream().sorted().toList();
    }

    private Integer resolveNotifyBefore(Integer notifyBefore) {
        int resolved = notifyBefore == null ? DEFAULT_NOTIFY_BEFORE : notifyBefore;
        if (!NotifyBeforeOption.supports(resolved)) {
            throw new CustomException(ErrorCode.INVALID_REMINDER_OFFSET);
        }
        return resolved;
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new CustomException(ErrorCode.INVALID_TASK_TITLE);
        }
    }

    private void validateDeadline(LocalDate deadline) {
        LocalDate today = LocalDate.now(clock);
        if (deadline == null || deadline.isBefore(today) || deadline.isAfter(today.plusYears(3))) {
            throw new CustomException(ErrorCode.INVALID_DUE_DATE);
        }
    }

    private LocalDate resolveCreateDeadline(LocalDate requestedDeadline, RepeatDeadlineCalculator.RepeatRuleData repeatRuleData) {
        if (repeatRuleData == null) {
            validateDeadline(requestedDeadline);
            return requestedDeadline;
        }
        return calculateRepeatDeadline(repeatRuleData);
    }

    private LocalDate calculateRepeatDeadline(RepeatDeadlineCalculator.RepeatRuleData repeatRuleData) {
        return repeatDeadlineCalculator.calculateInitialDeadline(repeatRuleData, LocalDate.now(clock))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_RECURRENCE_DATE_RANGE));
    }

    private Task getTaskByUser(Long taskId, Long userId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private User getUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private NotificationSetting getNotificationSetting(Task task) {
        return notificationSettingRepository.findByTaskId(task.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private TaskDetailResponse toDetailResponse(Long userId, Task task) {
        return TaskDetailResponse.from(
                task,
                getNotificationSetting(task),
                repeatRuleRepository.findByTaskId(task.getId()).orElse(null),
                taskSessionRepository.sumElapsedTimeByTaskIdAndUserId(task.getId(), userId)
        );
    }

    private Map<Long, Long> getElapsedTimeMap(Long userId, List<Task> tasks) {
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return taskSessionRepository.sumElapsedTimeByTaskIdsAndUserId(taskIds, userId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return escapeLikeKeyword(normalized);
    }

    private String escapeLikeKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private Pageable createPageable(int page, int size) {
        return org.springframework.data.domain.PageRequest.of(Math.max(page, 0), normalizeSize(size));
    }

    private Pageable createTaskPageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(
                        Sort.Order.asc("deadline"),
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id")
                )
        );
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

}

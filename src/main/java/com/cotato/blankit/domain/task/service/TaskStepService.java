package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.task.dto.request.TaskStepCreateRequest;
import com.cotato.blankit.domain.task.dto.request.TaskStepUpdateRequest;
import com.cotato.blankit.domain.task.dto.response.TaskStepResponse;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.entity.TaskStep;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.task.repository.TaskStepRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskStepService {

    private final TaskStepRepository taskStepRepository;
    private final TaskRepository taskRepository;

    public List<TaskStepResponse> getSteps(Long taskId, Long userId) {
        verifyTaskOwnership(taskId, userId);
        return taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskStepResponse createStep(Long taskId, Long userId, TaskStepCreateRequest request) {
        Task task = verifyTaskOwnership(taskId, userId);
        List<TaskStep> existing = taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(taskId);
        TaskStep step = taskStepRepository.save(TaskStep.create(task, request.title()));
        if (existing.isEmpty()) {
            task.updateProgressRate(0);
        } else {
            int total = existing.stream().mapToInt(TaskStep::getProgressRate).sum();
            task.updateProgressRate((int) Math.round((double) total / (existing.size() + 1)));
        }
        return toResponse(step);
    }

    @Transactional
    public TaskStepResponse updateStep(Long taskId, Long stepId, Long userId, TaskStepUpdateRequest request) {
        if (request.title() != null && request.title().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_TASK_TITLE);
        }
        Task task = verifyTaskOwnership(taskId, userId);
        TaskStep step = taskStepRepository.findByTaskStepIdAndTaskId(stepId, taskId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_STEP_NOT_FOUND));
        step.update(request.title(), request.progressRate());
        if (request.progressRate() != null) {
            recalculateTaskProgress(task, taskId);
        }
        return toResponse(step);
    }

    @Transactional
    public void deleteStep(Long taskId, Long stepId, Long userId) {
        Task task = verifyTaskOwnership(taskId, userId);
        TaskStep step = taskStepRepository.findByTaskStepIdAndTaskId(stepId, taskId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_STEP_NOT_FOUND));
        taskStepRepository.delete(step);
        recalculateTaskProgress(task, taskId);
    }

    public List<TaskStep> findStepsByTaskId(Long taskId) {
        return taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(taskId);
    }

    @Transactional
    public int applyAndCalculateProgress(Long taskId, Map<Long, Integer> stepProgressMap) {
        stepProgressMap.forEach((stepId, progressRate) -> {
            TaskStep step = taskStepRepository.findByTaskStepIdAndTaskId(stepId, taskId)
                    .orElseThrow(() -> new CustomException(ErrorCode.TASK_STEP_NOT_FOUND));
            step.update(null, progressRate);
        });
        List<TaskStep> all = taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(taskId);
        int total = all.stream().mapToInt(TaskStep::getProgressRate).sum();
        return (int) Math.round((double) total / all.size());
    }

    private Task verifyTaskOwnership(Long taskId, Long userId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
    }

    private void recalculateTaskProgress(Task task, Long taskId) {
        List<TaskStep> steps = taskStepRepository.findByTaskIdOrderByTaskStepIdAsc(taskId);
        if (steps.isEmpty()) {
            task.updateProgressRate(0);
            return;
        }
        int total = steps.stream().mapToInt(TaskStep::getProgressRate).sum();
        task.updateProgressRate((int) Math.round((double) total / steps.size()));
    }

    private TaskStepResponse toResponse(TaskStep step) {
        return new TaskStepResponse(
                step.getTaskStepId(),
                step.getTitle(),
                step.getProgressRate()
        );
    }
}

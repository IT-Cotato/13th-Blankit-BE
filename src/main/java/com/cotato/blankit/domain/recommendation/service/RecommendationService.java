package com.cotato.blankit.domain.recommendation.service;

import com.cotato.blankit.domain.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final TaskRepository taskRepository;

    public int calculateTodayRecommendedMinutes(Long userId) {
        LocalDate today = LocalDate.now();
        return taskRepository.findActiveTasksForRecommendation(userId, today)
                .stream()
                .mapToInt(task -> {
                    long daysRemaining = ChronoUnit.DAYS.between(today, task.getDeadline()) + 1;
                    return (int) Math.ceil((double) task.getEstimatedTime() / daysRemaining);
                })
                .sum();
    }
}

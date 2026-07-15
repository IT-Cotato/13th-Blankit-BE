package com.cotato.blankit.domain.task.service;

import com.cotato.blankit.domain.task.entity.RepeatRule;
import com.cotato.blankit.domain.task.entity.TaskStatus;
import com.cotato.blankit.domain.task.repository.RepeatRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RepeatDeadlineRefreshService {

    private final RepeatRuleRepository repeatRuleRepository;
    private final RepeatDeadlineCalculator repeatDeadlineCalculator;
    private final Clock clock;

    @Transactional
    public int refreshExpiredDeadlines() {
        LocalDate today = LocalDate.now(clock);
        List<RepeatRule> targets = repeatRuleRepository.findDeadlineRefreshTargets(
                today,
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS)
        );
        int updatedCount = 0;
        for (RepeatRule repeatRule : targets) {
            if (!today.isAfter(repeatRule.getTask().getDeadline())) {
                continue;
            }
            if (repeatDeadlineCalculator.calculateNextDeadline(repeatRule, today)
                    .map(nextDeadline -> {
                        repeatRule.getTask().updateDeadline(nextDeadline);
                        return true;
                    })
                    .orElse(false)) {
                updatedCount++;
            }
        }
        return updatedCount;
    }
}

package com.cotato.blankit.domain.notification.push.service;

import com.cotato.blankit.domain.task.entity.NotifyBeforeOption;
import com.cotato.blankit.domain.task.entity.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PushNotificationContentFactory {
    private final String taskDetailPath;
    private final String thirtyMinutePackPath;

    public PushNotificationContentFactory(
            @Value("${blankit.push.routes.task-detail:/tasks/{taskId}}") String taskDetailPath,
            @Value("${blankit.push.routes.thirty-minute-pack:/recommendations/pack30?availableMinutes={minutes}}")
            String thirtyMinutePackPath
    ) {
        this.taskDetailPath = taskDetailPath;
        this.thirtyMinutePackPath = thirtyMinutePackPath;
    }

    public Content taskDeadline(Task task, int notifyBeforeMinutes) {
        String label = NotifyBeforeOption.labelFor(notifyBeforeMinutes);
        return new Content(
                "과업 마감이 " + label + " 남았어요",
                task.getTitle() + " 과업을 확인해 주세요.",
                taskDetailPath.replace("{taskId}", String.valueOf(task.getId()))
        );
    }

    public Content thirtyMinutePack(long availableMinutes) {
        return new Content(
                "지금 30분 Pack을 시작해 볼까요?",
                availableMinutes + "분 동안 빠르게 진행할 수 있는 과업을 추천해 드려요.",
                thirtyMinutePackPath.replace("{minutes}", String.valueOf(availableMinutes))
        );
    }

    public record Content(String title, String body, String clickUrl) {
    }
}

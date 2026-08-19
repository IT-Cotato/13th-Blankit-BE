package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.category.entity.Category;
import com.cotato.blankit.domain.category.repository.CategoryRepository;
import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.notification.push.repository.PushNotificationJobRepository;
import com.cotato.blankit.domain.notification.push.service.TaskDeadlineNotificationScheduleService;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.task.entity.NotificationSetting;
import com.cotato.blankit.domain.task.entity.Task;
import com.cotato.blankit.domain.task.repository.NotificationSettingRepository;
import com.cotato.blankit.domain.task.repository.TaskRepository;
import com.cotato.blankit.domain.user.entity.SocialProvider;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "blankit.push.task-deadline.deadline-time=09:00")
@ActiveProfiles("test")
@Transactional
class TaskDeadlineNotificationScheduleServiceTest {
    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired TaskDeadlineNotificationScheduleService service;
    @Autowired PushNotificationJobRepository jobRepository;
    @Autowired NotificationSettingRepository notificationSettingRepository;
    @Autowired UserNotificationSettingRepository userSettingRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    User user;
    Task task;
    NotificationSetting taskSetting;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create(SocialProvider.KAKAO, "deadline-user",
                "deadline@example.com", "deadline", null, 60));
        Category category = categoryRepository.save(
                Category.create(user, "학업", "#123456", "book", 0, true));
        task = taskRepository.save(Task.create(
                user, category, "알고리즘 과제", LocalDate.of(2026, 6, 5), null, 60));
        taskSetting = notificationSettingRepository.save(NotificationSetting.create(task, 1440, true));
        UserNotificationSetting userSetting = UserNotificationSetting.createDefault(user);
        userSetting.update(true, false);
        userSettingRepository.save(userSetting);
    }

    @Test
    void oneDayNotificationIsTemporarilyScheduledTwoMinutesAfterCreation() {
        service.synchronizeTask(task.getId());

        var jobs = PushJobTestQueries.findByUserAndType(
                jobRepository, user.getId(), PushNotificationType.TASK_DEADLINE);
        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 2));
            assertThat(job.getReferenceType()).isEqualTo("TASK");
            assertThat(job.getReferenceId()).isEqualTo(String.valueOf(task.getId()));
            assertThat(job.getClickUrl()).isEqualTo("/tasks/" + task.getId());
        });
    }

    @Test
    void oneDayNotificationIsNotDueAtOneMinuteAndIsDueAtTwoMinutesWithoutDuplicateAfterSent() {
        service.synchronizeTask(task.getId());

        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 1, 9, 0);
        LocalDateTime leaseExpiredBefore = createdAt.minusMinutes(5);
        assertThat(jobRepository.findClaimableForUpdate(
                createdAt.plusMinutes(1), leaseExpiredBefore, PageRequest.of(0, 1))).isEmpty();

        var dueJobs = jobRepository.findClaimableForUpdate(
                createdAt.plusMinutes(2), leaseExpiredBefore, PageRequest.of(0, 1));
        assertThat(dueJobs).singleElement().satisfies(job -> {
            job.claim(createdAt.plusMinutes(2));
            job.markSent(createdAt.plusMinutes(2));
        });
        jobRepository.flush();

        assertThat(jobRepository.findClaimableForUpdate(
                createdAt.plusMinutes(3), leaseExpiredBefore, PageRequest.of(0, 1))).isEmpty();
    }

    @Test
    void threeDayNotificationKeepsOriginalDeadlineBasedSchedule() {
        taskSetting.update(4320, true);

        service.synchronizeTask(task.getId());

        assertThat(PushJobTestQueries.findByUserAndType(
                jobRepository, user.getId(), PushNotificationType.TASK_DEADLINE))
                .singleElement()
                .extracting(job -> job.getScheduledAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 2, 9, 0));
    }

    @Test
    void taskNotificationOffCancelsPendingJob() {
        service.synchronizeTask(task.getId());
        taskSetting = notificationSettingRepository.findByTaskId(task.getId()).orElseThrow();
        taskSetting.update(1440, false);

        service.synchronizeTask(task.getId());

        assertThat(PushJobTestQueries.findByUserAndType(
                jobRepository, user.getId(), PushNotificationType.TASK_DEADLINE))
                .allMatch(job -> job.getStatus() == PushNotificationJobStatus.CANCELLED);
    }

    @Test
    void serviceAlarmOffDoesNotSchedule() {
        UserNotificationSetting setting = userSettingRepository.findByUserId(user.getId()).orElseThrow();
        setting.update(false, false);

        service.synchronizeTask(task.getId());

        assertThat(PushJobTestQueries.findByUserAndType(
                jobRepository, user.getId(), PushNotificationType.TASK_DEADLINE)).isEmpty();
    }
}

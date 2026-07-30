package com.cotato.blankit.domain.notification.push;

import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationJobStatus;
import com.cotato.blankit.domain.notification.push.entity.PushNotificationType;
import com.cotato.blankit.domain.notification.push.repository.PushNotificationJobRepository;
import com.cotato.blankit.domain.notification.push.service.ThirtyMinutePackScheduleService;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.timetable.entity.Timetable;
import com.cotato.blankit.domain.timetable.dto.request.TimetableCreateRequest;
import com.cotato.blankit.domain.timetable.repository.TimetableRepository;
import com.cotato.blankit.domain.timetable.service.TimetableService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "blankit.push.thirty-minute-pack.horizon-days=7")
@ActiveProfiles("test")
@Transactional
class ThirtyMinutePackScheduleServiceTest {
    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired ThirtyMinutePackScheduleService service;
    @Autowired PushNotificationJobRepository jobRepository;
    @Autowired TimetableRepository timetableRepository;
    @Autowired UserNotificationSettingRepository settingRepository;
    @Autowired UserRepository userRepository;
    @Autowired TimetableService timetableService;
    User user;
    UserNotificationSetting setting;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create(SocialProvider.KAKAO, "pack-user", "pack@example.com",
                "pack", null, 60));
        setting = settingRepository.save(UserNotificationSetting.createDefault(user));
    }

    @Test
    void schedulesOnlyThirtyMinuteGapBetweenTwoTimetables() {
        setting.update(false, true);
        timetable((byte) 1, 10, 0, 11, 0);
        timetable((byte) 1, 11, 30, 12, 0); // 30분: 예약 대상
        timetable((byte) 1, 13, 0, 14, 0);  // 60분: 예약 제외

        service.synchronize(user.getId());

        var jobs = PushJobTestQueries.findByUserAndType(
                jobRepository, user.getId(), PushNotificationType.THIRTY_MIN_PACK);
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 11, 0));
        assertThat(jobs.get(0).getClickUrl()).contains("availableMinutes=30");
    }

    @Test
    void disablingSettingCancelsFuturePackJobs() {
        setting.update(false, true);
        timetable((byte) 1, 10, 0, 11, 0);
        timetable((byte) 1, 11, 30, 12, 0);
        service.synchronize(user.getId());

        setting = settingRepository.findByUserId(user.getId()).orElseThrow();
        setting.update(false, false);
        service.synchronize(user.getId());

        assertThat(PushJobTestQueries.findByUserAndType(
                jobRepository, user.getId(), PushNotificationType.THIRTY_MIN_PACK))
                .allMatch(job -> job.getStatus() == PushNotificationJobStatus.CANCELLED);
    }

    @Test
    void doesNotScheduleGapBeforeFirstOrAfterLastTimetable() {
        setting.update(false, true);
        timetable((byte) 1, 10, 0, 10, 30);

        service.synchronize(user.getId());

        assertThat(PushJobTestQueries.findByUserAndType(
                jobRepository, user.getId(), PushNotificationType.THIRTY_MIN_PACK)).isEmpty();
    }

    @Test
    void timetableCrudAutomaticallySynchronizesPackJobs() {
        setting.update(false, true);
        settingRepository.flush();

        timetableService.createTimetable(user.getId(), new TimetableCreateRequest(
                1, LocalTime.of(10, 0), LocalTime.of(11, 0), "앞 일정", null, "#123456"));
        timetableService.createTimetable(user.getId(), new TimetableCreateRequest(
                1, LocalTime.of(11, 30), LocalTime.of(12, 0), "뒤 일정", null, "#123456"));

        assertThat(PushJobTestQueries.findByUserAndType(
                jobRepository, user.getId(), PushNotificationType.THIRTY_MIN_PACK))
                .singleElement()
                .satisfies(job -> assertThat(job.getScheduledAt())
                        .isEqualTo(LocalDateTime.of(2026, 6, 1, 11, 0)));
    }

    private void timetable(byte day, int startHour, int startMinute, int endHour, int endMinute) {
        timetableRepository.save(Timetable.create(user, day, LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute), "일정", null, "#123456"));
    }
}

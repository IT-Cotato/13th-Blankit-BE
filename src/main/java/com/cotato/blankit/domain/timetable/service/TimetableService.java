package com.cotato.blankit.domain.timetable.service;

import com.cotato.blankit.domain.timetable.dto.request.TimetableCreateRequest;
import com.cotato.blankit.domain.notification.push.service.ThirtyMinutePackScheduleService;
import com.cotato.blankit.domain.timetable.dto.request.TimetableUpdateRequest;
import com.cotato.blankit.domain.timetable.dto.response.TimetableResponse;
import com.cotato.blankit.domain.timetable.dto.response.TimetableWithDisplayResponse;
import com.cotato.blankit.domain.timetable.dto.response.TimetablesWithDisplayResponse;
import com.cotato.blankit.domain.timetable.entity.Timetable;
import com.cotato.blankit.domain.timetable.repository.TimetableRepository;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final UserRepository userRepository;
    private final ThirtyMinutePackScheduleService thirtyMinutePackScheduleService;

    @Transactional(readOnly = true)
    public List<TimetableResponse> getTimetables(Long userId) {
        return timetableRepository.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId)
                .stream()
                .map(TimetableResponse::from)
                .toList();
    }

    @Transactional
    public TimetablesWithDisplayResponse createTimetables(Long userId, List<TimetableCreateRequest> requests) {
        User user = getUserForUpdate(userId);
        List<Timetable> saved = new ArrayList<>();
        for (TimetableCreateRequest request : requests) {
            validateTimeRange(request.startTime(), request.endTime());
            expandDisplayRangeIfNeeded(user, request.startTime(), request.endTime());
            checkTimeConflict(userId, request.dayOfWeek().byteValue(),
                    request.startTime(), request.endTime(), null);
            saved.add(timetableRepository.save(Timetable.create(
                    user,
                    request.dayOfWeek().byteValue(),
                    request.startTime(),
                    request.endTime(),
                    request.title(),
                    request.place(),
                    request.color()
            )));
        }
        timetableRepository.flush();
        thirtyMinutePackScheduleService.synchronize(userId);
        return new TimetablesWithDisplayResponse(
                saved.stream().map(TimetableResponse::from).toList(),
                user.getTimetableStartTime(),
                user.getTimetableEndTime()
        );
    }

    @Transactional
    public TimetableWithDisplayResponse updateTimetable(Long userId, Long timetableId, TimetableUpdateRequest request) {
        User user = getUserForUpdate(userId);
        Timetable timetable = getTimetable(userId, timetableId);

        byte targetDay = request.dayOfWeek() != null ? request.dayOfWeek().byteValue() : timetable.getDayOfWeek();
        LocalTime targetStart = request.startTime() != null ? request.startTime() : timetable.getStartTime();
        LocalTime targetEnd = request.endTime() != null ? request.endTime() : timetable.getEndTime();

        validateTimeRange(targetStart, targetEnd);
        expandDisplayRangeIfNeeded(user, targetStart, targetEnd);
        checkTimeConflict(userId, targetDay, targetStart, targetEnd, timetableId);

        timetable.update(
                request.dayOfWeek() != null ? request.dayOfWeek().byteValue() : null,
                request.startTime(),
                request.endTime(),
                request.title(),
                request.place(),
                request.color()
        );
        timetableRepository.flush();
        thirtyMinutePackScheduleService.synchronize(userId);
        return new TimetableWithDisplayResponse(
                TimetableResponse.from(timetable),
                user.getTimetableStartTime(),
                user.getTimetableEndTime()
        );
    }

    @Transactional
    public void deleteTimetable(Long userId, Long timetableId) {
        Timetable timetable = getTimetable(userId, timetableId);
        timetableRepository.delete(timetable);
        timetableRepository.flush();
        thirtyMinutePackScheduleService.synchronize(userId);
    }

    @Transactional
    public void deleteAllTimetables(Long userId) {
        timetableRepository.deleteByUserId(userId);
        thirtyMinutePackScheduleService.synchronize(userId);
    }

    private void expandDisplayRangeIfNeeded(User user, LocalTime blockStart, LocalTime blockEnd) {
        LocalTime currentStart = user.getTimetableStartTime();
        LocalTime currentEnd = user.getTimetableEndTime();

        LocalTime newStart = currentStart;
        LocalTime newEnd = currentEnd;

        if (blockStart.isBefore(currentStart)) {
            newStart = floorToHour(blockStart);
        }

        if (!currentEnd.equals(LocalTime.MIDNIGHT) && blockEnd.isAfter(currentEnd)) {
            newEnd = ceilToHour(blockEnd);
        }

        if (!newStart.equals(currentStart) || !newEnd.equals(currentEnd)) {
            user.updateTimetableSettings(newStart, newEnd);
        }
    }

    private LocalTime floorToHour(LocalTime time) {
        return time.withMinute(0).withSecond(0).withNano(0);
    }

    private LocalTime ceilToHour(LocalTime time) {
        if (time.getMinute() == 0 && time.getSecond() == 0) {
            return time;
        }
        return time.withMinute(0).withSecond(0).withNano(0).plusHours(1);
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new CustomException(ErrorCode.TIMETABLE_INVALID_TIME_RANGE);
        }
        if (startTime.getMinute() % 5 != 0 || endTime.getMinute() % 5 != 0) {
            throw new CustomException(ErrorCode.TIMETABLE_INVALID_TIME_UNIT);
        }
    }

    private void checkTimeConflict(Long userId, byte dayOfWeek, LocalTime startTime, LocalTime endTime, Long excludeId) {
        if (timetableRepository.existsTimeConflict(userId, dayOfWeek, startTime, endTime, excludeId)) {
            throw new CustomException(ErrorCode.TIMETABLE_TIME_CONFLICT);
        }
    }

    private Timetable getTimetable(Long userId, Long timetableId) {
        return timetableRepository.findByTimetableIdAndUserId(timetableId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TIMETABLE_NOT_FOUND));
    }

    private User getUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}

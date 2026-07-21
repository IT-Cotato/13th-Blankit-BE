package com.cotato.blankit.domain.timetable.service;

import com.cotato.blankit.domain.timetable.dto.request.TimetableCreateRequest;
import com.cotato.blankit.domain.timetable.dto.request.TimetableUpdateRequest;
import com.cotato.blankit.domain.timetable.dto.response.TimetableResponse;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TimetableResponse> getTimetables(Long userId) {
        return timetableRepository.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId)
                .stream()
                .map(TimetableResponse::from)
                .toList();
    }

    @Transactional
    public TimetableResponse createTimetable(Long userId, TimetableCreateRequest request) {
        validateTimeRange(request.startTime(), request.endTime());
        User user = getUserForUpdate(userId);
        checkTimeConflict(userId, request.dayOfWeek().byteValue(),
                request.startTime(), request.endTime(), null);
        Timetable timetable = Timetable.create(
                user,
                request.dayOfWeek().byteValue(),
                request.startTime(),
                request.endTime(),
                request.title(),
                request.place(),
                request.color()
        );
        return TimetableResponse.from(timetableRepository.save(timetable));
    }

    @Transactional
    public TimetableResponse updateTimetable(Long userId, Long timetableId, TimetableUpdateRequest request) {
        getUserForUpdate(userId);
        Timetable timetable = getTimetable(userId, timetableId);

        byte targetDay = request.dayOfWeek() != null ? request.dayOfWeek().byteValue() : timetable.getDayOfWeek();
        LocalTime targetStart = request.startTime() != null ? request.startTime() : timetable.getStartTime();
        LocalTime targetEnd = request.endTime() != null ? request.endTime() : timetable.getEndTime();

        validateTimeRange(targetStart, targetEnd);
        checkTimeConflict(userId, targetDay, targetStart, targetEnd, timetableId);

        timetable.update(
                request.dayOfWeek() != null ? request.dayOfWeek().byteValue() : null,
                request.startTime(),
                request.endTime(),
                request.title(),
                request.place(),
                request.color()
        );
        return TimetableResponse.from(timetable);
    }

    @Transactional
    public void deleteTimetable(Long userId, Long timetableId) {
        Timetable timetable = getTimetable(userId, timetableId);
        timetableRepository.delete(timetable);
    }

    @Transactional
    public void deleteAllTimetables(Long userId) {
        timetableRepository.deleteByUserId(userId);
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new CustomException(ErrorCode.TIMETABLE_INVALID_TIME_RANGE);
        }
        if (startTime.getMinute() % 30 != 0 || endTime.getMinute() % 30 != 0) {
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

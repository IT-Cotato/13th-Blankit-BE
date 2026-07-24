package com.cotato.blankit.domain.user.service;

import com.cotato.blankit.domain.auth.repository.RefreshTokenRepository;
import com.cotato.blankit.domain.user.dto.request.TimetableSettingsUpdateRequest;
import com.cotato.blankit.domain.user.dto.response.TimetableSettingsResponse;
import com.cotato.blankit.domain.user.dto.response.UserMeResponse;
import com.cotato.blankit.domain.user.entity.User;
import com.cotato.blankit.domain.user.repository.UserRepository;
import com.cotato.blankit.global.exception.CustomException;
import com.cotato.blankit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserMeResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        refreshTokenRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    @Transactional
    public TimetableSettingsResponse updateTimetableSettings(Long userId, TimetableSettingsUpdateRequest request) {
        validateTimetableSettings(request.startTime(), request.endTime());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.updateTimetableSettings(request.startTime(), request.endTime());
        return new TimetableSettingsResponse(user.getTimetableStartTime(), user.getTimetableEndTime());
    }

    private void validateTimetableSettings(LocalTime startTime, LocalTime endTime) {
        if (!endTime.equals(LocalTime.MIDNIGHT) && !startTime.isBefore(endTime)) {
            throw new CustomException(ErrorCode.INVALID_TIMETABLE_SETTINGS);
        }
    }
}

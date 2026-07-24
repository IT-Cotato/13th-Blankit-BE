package com.cotato.blankit.domain.user.service;

import com.cotato.blankit.domain.auth.repository.RefreshTokenRepository;
import com.cotato.blankit.domain.notification.entity.UserNotificationSetting;
import com.cotato.blankit.domain.notification.repository.UserNotificationSettingRepository;
import com.cotato.blankit.domain.user.dto.request.TimetableSettingsUpdateRequest;
import com.cotato.blankit.domain.user.dto.request.UserNotificationSettingUpdateRequest;
import com.cotato.blankit.domain.user.dto.response.TimetableSettingsResponse;
import com.cotato.blankit.domain.user.dto.response.UserMeResponse;
import com.cotato.blankit.domain.user.dto.response.UserNotificationSettingResponse;
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
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserNotificationSettingRepository userNotificationSettingRepository;

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
        userNotificationSettingRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public UserNotificationSettingResponse getNotificationSettings(Long userId) {
        validateUserExists(userId);
        return userNotificationSettingRepository.findByUserId(userId)
                .map(UserNotificationSettingResponse::from)
                .orElseGet(UserNotificationSettingResponse::defaultOff);
    }

    @Transactional
    public UserNotificationSettingResponse updateNotificationSettings(
            Long userId,
            UserNotificationSettingUpdateRequest request
    ) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        UserNotificationSetting setting = userNotificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> userNotificationSettingRepository.save(
                        UserNotificationSetting.createDefault(user)
                ));
        setting.update(request.isServiceAlarmEnabled(), request.is30minPackAlarmEnabled());
        return UserNotificationSettingResponse.from(setting);
    }

    @Transactional(readOnly = true)
    public List<Long> getServiceNotificationRecipientUserIds() {
        return userNotificationSettingRepository.findServiceNotificationRecipientUserIds();
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

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
    }
}

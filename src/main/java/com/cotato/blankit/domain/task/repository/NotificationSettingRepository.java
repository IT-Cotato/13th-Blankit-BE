package com.cotato.blankit.domain.task.repository;

import com.cotato.blankit.domain.task.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByTaskId(Long taskId);

    boolean existsByTaskId(Long taskId);
}

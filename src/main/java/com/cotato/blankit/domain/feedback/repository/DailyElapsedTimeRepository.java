package com.cotato.blankit.domain.feedback.repository;

import com.cotato.blankit.domain.feedback.entity.DailyElapsedTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyElapsedTimeRepository extends JpaRepository<DailyElapsedTime, Long> {

    Optional<DailyElapsedTime> findByTaskSession_TaskSessionIdAndDate(Long taskSessionId, LocalDate date);

    @Query("select coalesce(sum(d.elapsedSeconds), 0) from DailyElapsedTime d where d.user.id = :userId and d.date = :date")
    long sumElapsedSecondsByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    List<DailyElapsedTime> findByUser_IdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    @Modifying
    @Query("DELETE FROM DailyElapsedTime d WHERE d.taskSession.task.id = :taskId")
    void deleteByTaskSession_Task_Id(@Param("taskId") Long taskId);
}

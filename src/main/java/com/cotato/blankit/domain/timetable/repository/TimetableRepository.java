package com.cotato.blankit.domain.timetable.repository;

import com.cotato.blankit.domain.timetable.entity.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    List<Timetable> findByUserIdOrderByDayOfWeekAscStartTimeAsc(Long userId);

    Optional<Timetable> findByTimetableIdAndUserId(Long timetableId, Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Timetable t WHERE t.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT COUNT(t) > 0 FROM Timetable t
            WHERE t.user.id = :userId
              AND t.dayOfWeek = :dayOfWeek
              AND t.startTime < :endTime
              AND t.endTime > :startTime
              AND (:excludeId IS NULL OR t.timetableId <> :excludeId)
            """)
    boolean existsTimeConflict(@Param("userId") Long userId,
                               @Param("dayOfWeek") byte dayOfWeek,
                               @Param("startTime") LocalTime startTime,
                               @Param("endTime") LocalTime endTime,
                               @Param("excludeId") Long excludeId);
}

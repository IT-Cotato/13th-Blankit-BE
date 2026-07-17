package com.cotato.blankit.domain.task.repository;

import com.cotato.blankit.domain.task.entity.RepeatRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface RepeatRuleRepository extends JpaRepository<RepeatRule, Long> {

    Optional<RepeatRule> findByTaskId(Long taskId);

    List<RepeatRule> findByTaskIdIn(Collection<Long> taskIds);

    boolean existsByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);

    @Query("""
            select rr
            from RepeatRule rr
            join fetch rr.task t
            join fetch t.category
            left join fetch t.similarTask
            where t.deadline < :today
              and t.sourceTask is null
              and (rr.endDate is null or rr.endDate >= :today)
            """)
    List<RepeatRule> findOccurrenceGenerationTargets(@Param("today") LocalDate today);
}

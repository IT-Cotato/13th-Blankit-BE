package com.cotato.blankit.domain.task.repository;

import com.cotato.blankit.domain.task.entity.TaskSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TaskSessionRepository extends JpaRepository<TaskSession, Long> {

    @Query("""
            select coalesce(sum(ts.elapsedTime), 0)
            from TaskSession ts
            where ts.task.id = :taskId
              and ts.user.id = :userId
            """)
    long sumElapsedTimeByTaskIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Query("""
            select ts.task.id, coalesce(sum(ts.elapsedTime), 0)
            from TaskSession ts
            where ts.user.id = :userId
              and ts.task.id in :taskIds
            group by ts.task.id
            """)
    List<Object[]> sumElapsedTimeByTaskIdsAndUserId(
            @Param("taskIds") Collection<Long> taskIds,
            @Param("userId") Long userId
    );

    void deleteByTaskId(Long taskId);
}

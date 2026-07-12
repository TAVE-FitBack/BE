package com.fitback.domain.schedule.repository;

import com.fitback.domain.schedule.entity.TaskChecklist;
import com.fitback.domain.schedule.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskChecklistRepository extends JpaRepository<TaskChecklist, UUID> {

    Optional<TaskChecklist> findBySchedule_Id(UUID scheduleId);

    void deleteBySchedule_Id(UUID scheduleId);

    @Query("""
            select tc
            from TaskChecklist tc
            join fetch tc.schedule s
            where tc.id = :taskId
            """)
    Optional<TaskChecklist> findByIdWithSchedule(@Param("taskId") UUID taskId);

    @Query("""
            select tc
            from TaskChecklist tc
            join fetch tc.schedule s
            where tc.dueDate = :date
              and tc.taskType = :taskType
              and s.store.id = :storeId
            order by s.startAt asc, tc.createdAt asc, tc.id asc
            """)
    List<TaskChecklist> findAllByDateAndTaskTypeAndStoreIdOrderByScheduleStartAt(
            @Param("date") LocalDate date,
            @Param("taskType") TaskType taskType,
            @Param("storeId") UUID storeId
    );
}

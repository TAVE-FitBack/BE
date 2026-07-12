package com.fitback.domain.schedule.repository;

import com.fitback.domain.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    @Query("""
            select s
            from Schedule s
            where s.store.id = :storeId
              and s.startAt < :endExclusive
              and s.endAt > :startInclusive
            order by s.startAt asc, s.endAt asc, s.id asc
            """)
    List<Schedule> findOverlappingSchedules(
            @Param("storeId") UUID storeId,
            @Param("startInclusive") OffsetDateTime startInclusive,
            @Param("endExclusive") OffsetDateTime endExclusive
    );

    Optional<Schedule> findByIdAndStore_Id(UUID id, UUID storeId);
}

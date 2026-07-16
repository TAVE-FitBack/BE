package com.fitback.domain.event.repository;

import com.fitback.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findAllByStoreIdOrderByCreatedAtDesc(UUID storeId);

    Optional<Event> findByIdAndStoreId(UUID id, UUID storeId);
}
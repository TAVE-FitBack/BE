package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerActivityTimelineRepository extends JpaRepository<CustomerActivityTimeline, UUID> {
}

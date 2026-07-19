package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.FollowUpAiInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FollowUpAiInsightRepository extends JpaRepository<FollowUpAiInsight, UUID> {

    List<FollowUpAiInsight> findAllByFollowUpIdIn(Collection<UUID> followUpIds);
}

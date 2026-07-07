package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.CustomerAiInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerAiInsightRepository extends JpaRepository<CustomerAiInsight, UUID> {
}

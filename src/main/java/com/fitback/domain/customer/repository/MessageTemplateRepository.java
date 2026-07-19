package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    Optional<MessageTemplate> findByIdAndCustomer_Store_Id(UUID id, UUID storeId);

    Optional<MessageTemplate> findFirstByCustomerIdAndFollowUpIdOrderByGeneratedAtDesc(UUID customerId, UUID followUpId);

    Optional<MessageTemplate> findFirstByCustomerIdAndSentAtIsNotNullAndSentAtLessThanEqualOrderBySentAtDescIdDesc(
            UUID customerId,
            OffsetDateTime convertedAt
    );

    List<MessageTemplate> findAllByIdInAndCustomerIdAndCustomer_Store_Id(
            Collection<UUID> messageTemplateIds,
            UUID customerId,
            UUID storeId
    );
}

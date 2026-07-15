package com.fitback.domain.customer.service;

import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpConversion;
import com.fitback.domain.customer.entity.MessageTemplate;
import com.fitback.domain.customer.enums.ConversionSource;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.repository.FollowUpConversionRepository;
import com.fitback.domain.customer.repository.FollowUpRepository;
import com.fitback.domain.customer.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowUpConversionService {

    private final FollowUpConversionRepository followUpConversionRepository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final FollowUpRepository followUpRepository;

    @Transactional
    public boolean recordConversionIfAbsent(
            Customer customer,
            OffsetDateTime convertedAt,
            ConversionSource fallbackSource
    ) {
        Objects.requireNonNull(customer, "customer is required.");
        Objects.requireNonNull(customer.getId(), "customer id is required.");
        Objects.requireNonNull(convertedAt, "convertedAt is required.");

        if (followUpConversionRepository.existsByCustomer_Id(customer.getId())) {
            return false;
        }

        ConversionAttribution attribution = resolveAttribution(customer.getId(), convertedAt, fallbackSource);
        followUpConversionRepository.save(FollowUpConversion.builder()
                .customer(customer)
                .followUp(attribution.followUp())
                .messageTemplate(attribution.messageTemplate())
                .contactRound(attribution.contactRound())
                .convertedAt(convertedAt)
                .conversionSource(attribution.conversionSource())
                .build());

        return true;
    }

    private ConversionAttribution resolveAttribution(
            UUID customerId,
            OffsetDateTime convertedAt,
            ConversionSource fallbackSource
    ) {
        Optional<MessageTemplate> latestSentMessage = messageTemplateRepository
                .findFirstByCustomerIdAndSentAtIsNotNullAndSentAtLessThanEqualOrderBySentAtDescIdDesc(
                        customerId,
                        convertedAt
                );

        if (latestSentMessage.isPresent() && latestSentMessage.get().getFollowUp() != null) {
            MessageTemplate messageTemplate = latestSentMessage.get();
            return new ConversionAttribution(
                    ConversionSource.FOLLOW_UP_MESSAGE,
                    messageTemplate.getFollowUp(),
                    messageTemplate,
                    messageTemplate.getContactRound()
            );
        }

        FollowUp followUp = findAttributableFollowUp(customerId).orElse(null);
        if (followUp != null) {
            return new ConversionAttribution(
                    ConversionSource.MANUAL_STATUS_CHANGE,
                    followUp,
                    null,
                    followUp.getContactRound()
            );
        }

        return new ConversionAttribution(
                resolveFallbackSource(fallbackSource),
                null,
                null,
                null
        );
    }

    private Optional<FollowUp> findAttributableFollowUp(UUID customerId) {
        Optional<FollowUp> activeFollowUp = followUpRepository
                .findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING);

        if (activeFollowUp.isPresent()) {
            return activeFollowUp;
        }

        return followUpRepository.findFirstByCustomerIdOrderByCreatedAtDescIdDesc(customerId);
    }

    private ConversionSource resolveFallbackSource(ConversionSource fallbackSource) {
        if (fallbackSource == ConversionSource.RECONSULTATION
                || fallbackSource == ConversionSource.DIRECT_REGISTRATION) {
            return fallbackSource;
        }

        return ConversionSource.UNKNOWN;
    }

    private record ConversionAttribution(
            ConversionSource conversionSource,
            FollowUp followUp,
            MessageTemplate messageTemplate,
            Integer contactRound
    ) {
    }
}

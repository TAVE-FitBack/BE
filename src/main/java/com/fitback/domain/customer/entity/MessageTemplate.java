package com.fitback.domain.customer.entity;

import com.fitback.domain.customer.enums.MessageDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "message_template")
@Getter
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follow_up_id")
    private FollowUp followUp;

    @Column(name = "event_target_id", columnDefinition = "uuid")
    private UUID eventTargetId;

    @Column(name = "event_id", columnDefinition = "uuid")
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "message_id", length = 100)
    private String messageId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "version_type", nullable = false, length = 20)
    private String versionType;

    @Column(name = "tone_preset", nullable = false, length = 20)
    private String tonePreset;

    @Column(name = "delivery_status", length = 20)
    private String deliveryStatus;

    @Column(name = "contact_round")
    private Integer contactRound;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void markSent(OffsetDateTime sentAt) {
        this.deliveryStatus = MessageDeliveryStatus.SENT.name();
        this.sentAt = sentAt;
        this.updatedAt = sentAt;
    }

    public void assignContactRound(Integer contactRound) {
        if (contactRound != null) {
            FollowUp.validateContactRound(contactRound);
        }
        this.contactRound = contactRound;
    }
}

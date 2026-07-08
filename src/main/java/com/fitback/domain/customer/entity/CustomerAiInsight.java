package com.fitback.domain.customer.entity;

import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
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
@Table(name = "customer_ai_insight")
@Getter
public class CustomerAiInsight extends BaseTimeEntity {

    @Id
    @Column(name = "customer_id", columnDefinition = "uuid")
    private UUID customerId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "lead_temperature", length = 20)
    private String leadTemperature;

    @Column(name = "temperature_basis", columnDefinition = "text")
    private String temperatureBasis;

    @Column(name = "priority_score")
    private Integer priorityScore;

    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;

    public void updateAnalysis(String leadTemperature, String temperatureBasis, Integer priorityScore, OffsetDateTime analyzedAt) {
        this.leadTemperature = leadTemperature;
        this.temperatureBasis = temperatureBasis;
        this.priorityScore = priorityScore;
        this.analyzedAt = analyzedAt;
    }

    public void updateManualAnalysis(String leadTemperature, String temperatureBasis) {
        this.leadTemperature = leadTemperature;
        this.temperatureBasis = temperatureBasis;
    }
}

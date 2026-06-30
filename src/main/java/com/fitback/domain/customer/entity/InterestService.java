package com.fitback.domain.customer.entity;

import com.fitback.domain.service.entity.Service;
import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "interest_service",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_INTEREST_SERVICE_CUSTOMER_SERVICE",
                        columnNames = {"customer_id", "service_id"}
                )
        }
)
@Getter
public class InterestService extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;
}

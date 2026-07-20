package com.fitback.domain.customer.entity;

import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.store.entity.InflowPathOption;
import com.fitback.domain.store.entity.Store;
import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customer")
@Getter
public class Customer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_service_id")
    private Service registeredService;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "phone_num", nullable = false, length = 20)
    private String phoneNum;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_channel", nullable = false, length = 30)
    private PreferredContactChannel preferredContactChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inflow_path_id", nullable = false)
    private InflowPathOption inflowPathOption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Column(name = "registered_at")
    private OffsetDateTime registeredAt;

    @Column(name = "first_consult_at", nullable = false)
    private LocalDate firstConsultAt;

    @Column(name = "latest_consult_at", nullable = false)
    private LocalDate latestConsultAt;

    public void markRegistered(Service registeredService, OffsetDateTime registeredAt) {
        this.registeredService = registeredService;
        this.status = CustomerStatus.REGISTERED;
        if (this.registeredAt == null) {
            this.registeredAt = registeredAt;
        }
    }

    public void markStatus(CustomerStatus status) {
        this.status = status;
        this.registeredService = null;
    }

    public void updateLatestConsultAt(LocalDate latestConsultAt) {
        this.latestConsultAt = latestConsultAt;
    }

    public void updateFirstConsultAt(LocalDate firstConsultAt) {
        this.firstConsultAt = firstConsultAt;
    }

    public void updateBasicInfo(String name, Gender gender, LocalDate birthDate, String phoneNum) {
        this.name = name;
        this.gender = gender;
        this.birthDate = birthDate;
        this.phoneNum = phoneNum;
    }
}

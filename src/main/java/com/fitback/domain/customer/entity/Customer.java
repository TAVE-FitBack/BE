package com.fitback.domain.customer.entity;

import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.InflowPath;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.store.entity.Store;
import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
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
    @Column(length = 10)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_num", length = 20)
    private String phoneNum;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_channel", length = 30)
    private PreferredContactChannel preferredContactChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "inflow_path", length = 30)
    private InflowPath inflowPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Column(name = "first_consult_at", nullable = false)
    private LocalDate firstConsultAt;

    @Column(name = "latest_consult_at", nullable = false)
    private LocalDate latestConsultAt;

    public void updateBasicInfo(
            String name,
            Gender gender,
            LocalDate birthDate,
            String phoneNum,
            PreferredContactChannel preferredContactChannel,
            InflowPath inflowPath,
            LocalDate latestConsultAt
    ) {
        this.name = name;
        this.gender = gender;
        this.birthDate = birthDate;
        this.phoneNum = phoneNum;
        this.preferredContactChannel = preferredContactChannel;
        this.inflowPath = inflowPath;
        this.latestConsultAt = latestConsultAt;
    }

    public void markRegistered(Service registeredService) {
        this.registeredService = registeredService;
        this.status = CustomerStatus.REGISTERED;
    }

    public void markUnregistered() {
        this.status = CustomerStatus.UNREGISTERED;
    }
}

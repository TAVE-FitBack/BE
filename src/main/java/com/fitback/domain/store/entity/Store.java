package com.fitback.domain.store.entity;

import com.fitback.domain.store.enums.StoreType;
import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "store")
@Getter
public class Store extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "store_type", nullable = false, length = 50)
    private StoreType storeType;

    @Column(length = 20)
    private String phone;

    @Column(name = "business_number", length = 12)
    private String businessNumber;

    @Column(name = "address", length = 200)
    private String address;
}
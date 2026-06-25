package com.fitback.core.infrastructure.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant_records", indexes = {
        @Index(name = "idx_tenant_records_tenant_collection", columnList = "tenant_id, collection")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TenantRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 200)
    private String tenantId;

    @Column(name = "collection", nullable = false, length = 100)
    private String collection;

    @Column(name = "data_json", nullable = false, columnDefinition = "text")
    private String dataJson;
}

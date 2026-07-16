package com.fitback.domain.store.entity;

import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "inflow_path_option",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_INFLOW_PATH_OPTION_STORE_NAME",
                        columnNames = {"store_id", "name"}
                )
        }
)
@Getter
public class InflowPathOption extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public void update(String name, int displayOrder, boolean active) {
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = active;
    }
}
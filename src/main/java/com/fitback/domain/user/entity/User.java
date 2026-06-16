package com.fitback.domain.user.entity;

import com.fitback.domain.store.entity.Store;
import com.fitback.domain.user.enums.UserRole;
import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
@Getter
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(nullable = false, length = 50, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(nullable = false)
    private String password;

    @Column(name = "agree_marketing", nullable = false)
    private boolean agreeMarketing;

    @Column(name = "agree_terms", nullable = false)
    private boolean agreeTerms;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    public void assignStore(Store store) {
        this.store = store;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }
}
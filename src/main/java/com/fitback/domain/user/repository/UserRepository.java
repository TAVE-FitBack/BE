package com.fitback.domain.user.repository;

import com.fitback.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByIdAndStore_Id(UUID id, UUID storeId);
    List<User> findAllByStore_Id(UUID storeId);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
}

package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByPhoneNumAndStoreId(String phoneNum, UUID storeId);

    Optional<Customer> findByIdAndStoreId(UUID id, UUID storeId);

    boolean existsByPhoneNumAndStoreIdAndIdNot(String phoneNum, UUID storeId, UUID id);
}

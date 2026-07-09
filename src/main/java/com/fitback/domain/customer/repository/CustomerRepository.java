package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByPhoneNumAndStoreId(String phoneNum, UUID storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Customer c
            where c.phoneNum = :phoneNum
              and c.store.id = :storeId
            """)
    Optional<Customer> findByPhoneNumAndStoreIdForUpdate(
            @Param("phoneNum") String phoneNum,
            @Param("storeId") UUID storeId
    );

    boolean existsByIdAndStoreId(UUID id, UUID storeId);
}

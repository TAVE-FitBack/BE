package com.fitback.domain.store.service;

import com.fitback.domain.store.dto.request.StoreSetupRequest;
import com.fitback.domain.store.dto.response.StoreSetupResponse;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.exception.StoreErrorCode;
import com.fitback.domain.store.repository.StoreRepository;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.exception.UserErrorCode;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    /* 매장 초기 설정 */
    @Transactional
    public StoreSetupResponse setup(UUID userId, StoreSetupRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (user.getStore() != null) {
            throw new BusinessException(StoreErrorCode
                    .STORE_ALREADY_EXISTS);
        }

        Store store = Store.builder()
                .name(request.getName())
                .storeType(request.getStoreType())
                .phone(request.getPhone())
                .address(request.getAddress())
                .businessNumber(request.getBusinessNumber())
                .customStoreType(request.getCustomStoreType())
                .build();
        storeRepository.save(store);

        user.assignStore(store);

        return StoreSetupResponse.builder()
                .storeId(store.getId())
                .name(store.getName())
                .storeType(store.getStoreType())
                .build();
    }
}

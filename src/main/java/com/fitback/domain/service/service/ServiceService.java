package com.fitback.domain.service.service;

import com.fitback.domain.service.dto.request.ServiceCreateRequest;
import com.fitback.domain.service.dto.request.ServiceUpdateRequest;
import com.fitback.domain.service.dto.response.ServiceResponse;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.exception.ServiceErrorCode;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.exception.StoreErrorCode;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.exception.UserErrorCode;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    /* 서비스 목록 조회 */
    public List<ServiceResponse> getServices(UUID userId) {
        Store store = getStore(userId);
        return serviceRepository.findAllByStoreId(store.getId())
                .stream()
                .map(ServiceResponse::from)
                .toList();
    }

    /* 서비스 등록 */
    @Transactional
    public ServiceResponse createService(UUID userId, ServiceCreateRequest request) {
        Store store = getStore(userId);

        Service service = serviceRepository.save(
                Service.builder()
                        .store(store)
                        .name(request.getName())
                        .description(request.getDescription())
                        .price(request.getPrice())
                        .active(request.isActive())
                        .build()
        );

        return ServiceResponse.from(service);
    }

    /* 서비스 수정 */
    @Transactional
    public ServiceResponse updateService(UUID userId, UUID serviceId, ServiceUpdateRequest request) {
        Store store = getStore(userId);

        Service service = serviceRepository.findByIdAndStoreId(serviceId, store.getId())
                .orElseThrow(() -> new BusinessException(ServiceErrorCode.SERVICE_NOT_FOUND));

        service.update(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.isActive()
        );

        return ServiceResponse.from(service);
    }

    /* 서비스 삭제 */
    @Transactional
    public void deleteService(UUID userId, UUID serviceId) {
        Store store = getStore(userId);

        Service service = serviceRepository.findByIdAndStoreId(serviceId, store.getId())
                .orElseThrow(() -> new BusinessException(ServiceErrorCode.SERVICE_NOT_FOUND));

        serviceRepository.delete(service);
    }

    private Store getStore(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (user.getStore() == null) {
            throw new BusinessException(StoreErrorCode.STORE_NOT_FOUND);
        }

        return user.getStore();
    }
}

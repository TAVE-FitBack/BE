package com.fitback.domain.store.service;

import com.fitback.domain.store.dto.request.InflowPathCreateRequest;
import com.fitback.domain.store.dto.request.InflowPathUpdateRequest;
import com.fitback.domain.store.dto.response.InflowPathResponse;
import com.fitback.domain.store.entity.InflowPathOption;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.exception.StoreErrorCode;
import com.fitback.domain.store.repository.InflowPathOptionRepository;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.exception.UserErrorCode;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InflowPathService {

    private final InflowPathOptionRepository inflowPathOptionRepository;
    private final UserRepository userRepository;

    /* 매장 방문 경로 목록 조회 */
    public List<InflowPathResponse> getInflowPaths(UUID userId) {
        Store store = getStore(userId);
        return inflowPathOptionRepository.findAllByStoreIdOrderByDisplayOrder(store.getId())
                .stream()
                .map(InflowPathResponse::from)
                .toList();
    }

    /* 매장 방문 경로 등록 */
    @Transactional
    public InflowPathResponse createInflowPath(UUID userId, InflowPathCreateRequest request) {
        Store store = getStore(userId);

        InflowPathOption option = InflowPathOption.builder()
                .store(store)
                .name(request.getName())
                .displayOrder(request.getDisplayOrder())
                .active(request.isActive())
                .build();
        inflowPathOptionRepository.save(option);

        return InflowPathResponse.from(option);
    }

    /* 매장 방문 경로 수정 */
    @Transactional
    public InflowPathResponse updateInflowPath(UUID userId, UUID inflowPathId, InflowPathUpdateRequest request) {
        Store store = getStore(userId);

        InflowPathOption option = inflowPathOptionRepository.findByIdAndStoreId(inflowPathId, store.getId())
                .orElseThrow(() -> new BusinessException(StoreErrorCode.INFLOW_PATH_NOT_FOUND));

        option.update(request.getName(), request.getDisplayOrder(), request.isActive());

        return InflowPathResponse.from(option);
    }

    /* 매장 방문 경로 삭제 */
    @Transactional
    public void deleteService(UUID userId, UUID inflowPathId) {
        Store store = getStore(userId);

        InflowPathOption inflowPathOption = inflowPathOptionRepository.findByIdAndStoreId(inflowPathId, store.getId())
                .orElseThrow(() -> new BusinessException(StoreErrorCode.INFLOW_PATH_NOT_FOUND));

        inflowPathOptionRepository.delete(inflowPathOption);
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
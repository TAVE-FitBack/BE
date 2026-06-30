package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.dto.response.ConsultationNewResponse;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationService {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    public ConsultationNewResponse getNewConsultationData(UUID storeId) {
        if (storeId == null) {
            throw new BusinessException(ConsultationErrorCode.STORE_NOT_ASSIGNED);
        }

        List<ConsultationNewResponse.ServiceInfo> services = serviceRepository
                .findAllByStoreIdAndActiveTrue(storeId)
                .stream()
                .map(service -> ConsultationNewResponse.ServiceInfo.builder()
                        .serviceId(service.getId())
                        .name(service.getName())
                        .build())
                .toList();

        List<ConsultationNewResponse.CounselorInfo> counselors = userRepository
                .findAllByStoreId(storeId)
                .stream()
                .map(user -> ConsultationNewResponse.CounselorInfo.builder()
                        .userId(user.getId())
                        .name(user.getNickname())
                        .build())
                .toList();

        return ConsultationNewResponse.builder()
                .services(services)
                .counselors(counselors)
                .build();
    }
}

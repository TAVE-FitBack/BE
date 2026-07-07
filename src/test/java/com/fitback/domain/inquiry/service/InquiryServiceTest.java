package com.fitback.domain.inquiry.service;

import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.repository.InflowPathOptionRepository;
import com.fitback.domain.inquiry.dto.response.InquiryNewResponse;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import com.fitback.domain.inquiry.exception.InquiryErrorCode;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.enums.StoreType;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.enums.UserRole;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private InflowPathOptionRepository inflowPathOptionRepository;

    @Mock
    private UserRepository userRepository;

    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryService = new InquiryService(
                serviceRepository,
                inflowPathOptionRepository,
                userRepository
        );
    }

    @Test
    @DisplayName("문의 등록 초기 데이터 조회 시 매장이 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void getNewInquiryDataStoreNotAssigned() {
        assertThatThrownBy(() -> inquiryService.getNewInquiryData(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(serviceRepository, inflowPathOptionRepository, userRepository);
    }

    @Test
    @DisplayName("문의 등록 초기 데이터로 활성 서비스, 활성 문의 경로 옵션, 상담자, 문의 상태 목록을 반환한다")
    void getNewInquiryData() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();

        Store store = Store.builder()
                .id(storeId)
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
        Service service = Service.builder()
                .id(serviceId)
                .store(store)
                .name("PT")
                .active(true)
                .build();
        InflowPathOption inflowPathOption = InflowPathOption.builder()
                .id(inflowPathId)
                .store(store)
                .name("워크인")
                .displayOrder(1)
                .active(true)
                .build();
        User counselor = User.builder()
                .id(counselorId)
                .store(store)
                .email("coach@fitback.test")
                .nickname("김코치")
                .role(UserRole.STAFF)
                .password("password")
                .agreeMarketing(false)
                .agreeTerms(true)
                .emailVerified(true)
                .build();

        when(serviceRepository.findAllByStoreIdAndActiveTrue(storeId))
                .thenReturn(List.of(service));
        when(inflowPathOptionRepository.findAllByStoreIdAndActiveTrueOrderByDisplayOrderAsc(storeId))
                .thenReturn(List.of(inflowPathOption));
        when(userRepository.findAllByStore_Id(storeId))
                .thenReturn(List.of(counselor));

        InquiryNewResponse response = inquiryService.getNewInquiryData(storeId);

        assertThat(response.getServices()).hasSize(1);
        assertThat(response.getServices().get(0).getServiceId()).isEqualTo(serviceId);
        assertThat(response.getServices().get(0).getName()).isEqualTo("PT");
        assertThat(response.getInflowPaths()).hasSize(1);
        assertThat(response.getInflowPaths().get(0).getInflowPathId()).isEqualTo(inflowPathId);
        assertThat(response.getInflowPaths().get(0).getName()).isEqualTo("워크인");
        assertThat(response.getInflowPaths().get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(response.getCounselors()).hasSize(1);
        assertThat(response.getCounselors().get(0).getUserId()).isEqualTo(counselorId);
        assertThat(response.getCounselors().get(0).getName()).isEqualTo("김코치");
        assertThat(response.getInquiryStatuses())
                .extracting("status")
                .containsExactly(
                        InquiryStatus.RECEIVED,
                        InquiryStatus.VISIT_SCHEDULED,
                        InquiryStatus.VISIT_CANCELED
                );
        assertThat(response.getInquiryStatuses())
                .extracting("label")
                .containsExactly("문의 접수", "방문 예정", "방문 취소");

        verify(serviceRepository).findAllByStoreIdAndActiveTrue(storeId);
        verify(inflowPathOptionRepository).findAllByStoreIdAndActiveTrueOrderByDisplayOrderAsc(storeId);
        verify(userRepository).findAllByStore_Id(storeId);
    }
}

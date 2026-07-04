package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.response.ConsultationNewResponse;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.customer.repository.InflowPathOptionRepository;
import com.fitback.domain.customer.repository.InterestServiceRepository;
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
class ConsultationServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private InflowPathOptionRepository inflowPathOptionRepository;

    @Mock
    private InterestServiceRepository interestServiceRepository;

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private CustomerActivityTimelineRepository customerActivityTimelineRepository;

    @Mock
    private AiConsultationClient aiConsultationClient;

    private ConsultationService consultationService;

    @BeforeEach
    void setUp() {
        consultationService = new ConsultationService(
                serviceRepository,
                userRepository,
                customerRepository,
                inflowPathOptionRepository,
                interestServiceRepository,
                consultationRepository,
                customerActivityTimelineRepository,
                aiConsultationClient
        );
    }

    @Test
    @DisplayName("상담 등록 초기 데이터 조회 시 매장이 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void getNewConsultationDataStoreNotAssigned() {
        assertThatThrownBy(() -> consultationService.getNewConsultationData(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(serviceRepository, inflowPathOptionRepository, userRepository);
    }

    @Test
    @DisplayName("상담 등록 초기 데이터로 활성 서비스, 활성 방문경로 옵션, 상담자 목록을 반환한다")
    void getNewConsultationData() {
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
                .name("네이버 검색")
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

        ConsultationNewResponse response = consultationService.getNewConsultationData(storeId);

        assertThat(response.getServices()).hasSize(1);
        assertThat(response.getServices().get(0).getServiceId()).isEqualTo(serviceId);
        assertThat(response.getServices().get(0).getName()).isEqualTo("PT");
        assertThat(response.getInflowPaths()).hasSize(1);
        assertThat(response.getInflowPaths().get(0).getInflowPathId()).isEqualTo(inflowPathId);
        assertThat(response.getInflowPaths().get(0).getName()).isEqualTo("네이버 검색");
        assertThat(response.getInflowPaths().get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(response.getCounselors()).hasSize(1);
        assertThat(response.getCounselors().get(0).getUserId()).isEqualTo(counselorId);
        assertThat(response.getCounselors().get(0).getName()).isEqualTo("김코치");

        verify(serviceRepository).findAllByStoreIdAndActiveTrue(storeId);
        verify(inflowPathOptionRepository).findAllByStoreIdAndActiveTrueOrderByDisplayOrderAsc(storeId);
        verify(userRepository).findAllByStore_Id(storeId);
    }
}

package com.fitback.domain.event.service;

import com.fitback.domain.event.dto.request.EventCreateRequest;
import com.fitback.domain.event.dto.request.EventUpdateRequest;
import com.fitback.domain.event.dto.response.EventResponse;
import com.fitback.domain.event.entity.Event;
import com.fitback.domain.event.enums.EventStatus;
import com.fitback.domain.event.exception.EventErrorCode;
import com.fitback.domain.event.repository.EventRepository;
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
public class EventService {

    private final EventRepository eventRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    /* 이벤트 목록 조회 */
    public List<EventResponse> getEvents(UUID userId) {
        Store store = getStore(userId);
        return eventRepository.findAllByStoreIdOrderByCreatedAtDesc(store.getId())
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    /* 이벤트 등록 */
    @Transactional
    public EventResponse createEvent(UUID userId, EventCreateRequest request) {
        Store store = getStore(userId);

        // 종료일이 시작일보다 앞선 경우
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(EventErrorCode.INVALID_EVENT_DATE);
        }

        Service service = resolveService(request.getServiceId(), store.getId());

        Event event = Event.builder()
                .store(store)
                .service(service)
                .title(request.getTitle())
                .eventType(request.getEventType())
                .description(request.getDescription())
                .discountRate(request.getDiscountRate())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(EventStatus.ACTIVE)
                .build();

        eventRepository.save(event);

        return EventResponse.from(event);
    }

    /* 이벤트 수정 */
    @Transactional
    public EventResponse updateEvent(UUID userId, UUID eventId, EventUpdateRequest request) {
        Store store = getStore(userId);

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(EventErrorCode.INVALID_EVENT_DATE);
        }

        Service service = resolveService(request.getServiceId(), store.getId());

        Event event = eventRepository.findByIdAndStoreId(eventId, store.getId())
                .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));

        event.update(
                request.getTitle(),
                request.getEventType(),
                request.getDescription(),
                request.getDiscountRate(),
                request.getStartDate(),
                request.getEndDate(),
                request.getStatus(),
                service
        );

        return EventResponse.from(event);
    }

    /* 이벤트 삭제 */
    @Transactional
    public void deleteEvent(UUID userId, UUID eventId) {
        Store store = getStore(userId);

        Event event = eventRepository.findByIdAndStoreId(eventId, store.getId())
                .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND));

        eventRepository.delete(event);
    }

    private Service resolveService(UUID serviceId, UUID storeId) {
        if (serviceId == null) return null;

        return serviceRepository.findByIdAndStoreId(serviceId, storeId)
                .orElseThrow(() -> new BusinessException(ServiceErrorCode.SERVICE_NOT_FOUND));
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
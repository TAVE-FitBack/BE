package com.fitback.domain.customer.service;

import com.fitback.domain.customer.dto.request.FollowUpBoardQuery;
import com.fitback.domain.customer.dto.request.FollowUpEndedQuery;
import com.fitback.domain.customer.dto.response.FollowUpBoardColumnResponse;
import com.fitback.domain.customer.dto.response.FollowUpBoardItemResponse;
import com.fitback.domain.customer.dto.response.FollowUpBoardResponse;
import com.fitback.domain.customer.dto.response.FollowUpEndedItemResponse;
import com.fitback.domain.customer.dto.response.FollowUpEndedListResponse;
import com.fitback.domain.customer.dto.response.FollowUpSummaryResponse;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.exception.FollowUpManagementErrorCode;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.BoardCondition;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.BoardRow;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.EndedCondition;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.EndedPageRows;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.EndedRow;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.NonConversionReasonRow;
import com.fitback.domain.customer.repository.FollowUpManagementQueryRepository.SummaryCounts;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowUpManagementService {

    private static final String TAB_TODAY = "TODAY";
    private static final String TAB_SCHEDULED = "SCHEDULED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Map<String, String> REASON_DISPLAY_NAMES = Map.of(
            "PRICE_BURDEN", "이용료 부담",
            "SCHEDULE_CONFLICT", "일정 문제"
    );

    private final FollowUpManagementQueryRepository queryRepository;

    public FollowUpSummaryResponse getSummary(UUID storeId, String month) {
        validateStoreId(storeId);

        LocalDate today = LocalDate.now(Clock.system(CustomerManagementQueryValidator.SERVICE_ZONE_ID));
        YearMonth targetMonth = resolveMonth(month);
        SummaryCounts counts = queryRepository.findSummaryCounts(
                storeId,
                today,
                targetMonth.atDay(1),
                targetMonth.plusMonths(1).atDay(1)
        );

        return FollowUpSummaryResponse.builder()
                .todayPendingCount(counts.todayPendingCount())
                .secondRoundPendingCount(counts.secondRoundPendingCount())
                .overdueCount(counts.overdueCount())
                .thisMonthPendingCount(counts.thisMonthPendingCount())
                .build();
    }

    public FollowUpBoardResponse getBoard(UUID storeId, FollowUpBoardQuery query) {
        validateStoreId(storeId);
        BoardCondition condition = buildBoardCondition(query);
        LocalDate today = LocalDate.now(Clock.system(CustomerManagementQueryValidator.SERVICE_ZONE_ID));

        List<BoardRow> rows = queryRepository.findBoardRows(storeId, condition, today);
        Map<UUID, List<FollowUpBoardItemResponse.NonConversionReasonInfo>> reasonsByCustomer =
                findBoardReasonsByCustomer(rows);
        Map<Integer, List<FollowUpBoardItemResponse>> itemsByRound = rows.stream()
                .map(row -> toBoardItem(
                        row,
                        reasonsByCustomer.getOrDefault(row.customerId(), List.of())
                ))
                .collect(Collectors.groupingBy(FollowUpBoardItemResponse::getContactRound));

        List<FollowUpBoardColumnResponse> columns = IntStream.rangeClosed(1, 3)
                .mapToObj(round -> toBoardColumn(
                        round,
                        itemsByRound.getOrDefault(round, List.of())
                ))
                .toList();

        return FollowUpBoardResponse.builder()
                .tab(condition.tab())
                .baseDate(today)
                .columns(columns)
                .build();
    }

    public FollowUpEndedListResponse getEndedFollowUps(UUID storeId, FollowUpEndedQuery query) {
        validateStoreId(storeId);
        EndedCondition condition = buildEndedCondition(query);

        EndedPageRows pageRows = queryRepository.findEndedRows(
                storeId,
                condition,
                query.getPage(),
                query.getSize()
        );
        Map<UUID, List<FollowUpEndedItemResponse.NonConversionReasonInfo>> reasonsByCustomer =
                findEndedReasonsByCustomer(pageRows.content());
        List<FollowUpEndedItemResponse> items = pageRows.content().stream()
                .map(row -> toEndedItem(
                        row,
                        reasonsByCustomer.getOrDefault(row.customerId(), List.of())
                ))
                .toList();
        int totalPages = pageRows.totalElements() == 0
                ? 0
                : (int) Math.ceil((double) pageRows.totalElements() / query.getSize());

        return FollowUpEndedListResponse.builder()
                .items(items)
                .page(query.getPage())
                .size(query.getSize())
                .totalElements(pageRows.totalElements())
                .totalPages(totalPages)
                .build();
    }

    private void validateStoreId(UUID storeId) {
        if (storeId == null) {
            throw new BusinessException(FollowUpManagementErrorCode.STORE_NOT_ASSIGNED);
        }
    }

    private BoardCondition buildBoardCondition(FollowUpBoardQuery query) {
        if (query == null) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }
        String tab = normalizeUppercase(query.getTab());
        if (tab == null || (!TAB_TODAY.equals(tab) && !TAB_SCHEDULED.equals(tab))) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }
        Integer contactRound = query.getContactRound();
        if (contactRound != null && (contactRound < 1 || contactRound > 3)) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }

        return new BoardCondition(
                tab,
                normalizeKeyword(query.getKeyword()),
                contactRound,
                query.getHasReply()
        );
    }

    private EndedCondition buildEndedCondition(FollowUpEndedQuery query) {
        if (query == null) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }
        validatePage(query.getPage(), query.getSize());

        Integer contactRound = query.getContactRound();
        if (contactRound != null && (contactRound < 1 || contactRound > 3)) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }

        String followUpStatus = normalizeUppercase(query.getFollowUpStatus());
        if (followUpStatus != null
                && (!STATUS_COMPLETED.equals(followUpStatus) && !STATUS_CLOSED.equals(followUpStatus))) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }

        LocalDate startDate = query.getStartDate();
        LocalDate endDate = query.getEndDate();
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_DATE_RANGE);
        }

        return new EndedCondition(
                normalizeKeyword(query.getKeyword()),
                contactRound,
                query.getHasReply(),
                followUpStatus,
                startDate,
                endDate
        );
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > CustomerManagementQueryValidator.MAX_PAGE_SIZE) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > CustomerManagementQueryValidator.MAX_KEYWORD_LENGTH) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeUppercase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<UUID, List<FollowUpBoardItemResponse.NonConversionReasonInfo>>
    findBoardReasonsByCustomer(List<BoardRow> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> customerIds = rows.stream()
                .map(BoardRow::customerId)
                .collect(Collectors.toSet());

        return queryRepository.findNonConversionReasons(customerIds).stream()
                .collect(Collectors.groupingBy(
                        NonConversionReasonRow::customerId,
                        Collectors.mapping(
                                this::toBoardReasonInfo,
                                Collectors.toList()
                        )
                ));
    }

    private Map<UUID, List<FollowUpEndedItemResponse.NonConversionReasonInfo>>
    findEndedReasonsByCustomer(List<EndedRow> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> customerIds = rows.stream()
                .map(EndedRow::customerId)
                .collect(Collectors.toSet());

        return queryRepository.findNonConversionReasons(customerIds).stream()
                .collect(Collectors.groupingBy(
                        NonConversionReasonRow::customerId,
                        Collectors.mapping(
                                this::toEndedReasonInfo,
                                Collectors.toList()
                        )
                ));
    }

    private FollowUpBoardItemResponse.NonConversionReasonInfo toBoardReasonInfo(
            NonConversionReasonRow row
    ) {
        return FollowUpBoardItemResponse.NonConversionReasonInfo.builder()
                .reasonType(row.reasonType())
                .displayName(REASON_DISPLAY_NAMES.getOrDefault(row.reasonType(), row.reasonType()))
                .build();
    }

    private FollowUpEndedItemResponse.NonConversionReasonInfo toEndedReasonInfo(
            NonConversionReasonRow row
    ) {
        return FollowUpEndedItemResponse.NonConversionReasonInfo.builder()
                .reasonType(row.reasonType())
                .displayName(REASON_DISPLAY_NAMES.getOrDefault(row.reasonType(), row.reasonType()))
                .build();
    }

    private FollowUpBoardColumnResponse toBoardColumn(
            int contactRound,
            List<FollowUpBoardItemResponse> items
    ) {
        return FollowUpBoardColumnResponse.builder()
                .contactRound(contactRound)
                .title(contactRound + "차 연락 대상자")
                .count(items.size())
                .items(items)
                .build();
    }

    private FollowUpBoardItemResponse toBoardItem(
            BoardRow row,
            List<FollowUpBoardItemResponse.NonConversionReasonInfo> reasons
    ) {
        return FollowUpBoardItemResponse.builder()
                .followUpId(row.followUpId())
                .customerId(row.customerId())
                .customerName(row.customerName())
                .phoneNum(row.phoneNum())
                .gender(Gender.valueOf(row.gender()))
                .serviceName(row.serviceName())
                .customerStatus(CustomerStatus.valueOf(row.customerStatus()))
                .followUpStatus(FollowUpStatus.valueOf(row.followUpStatus()))
                .contactRound(row.contactRound())
                .recommendContactDate(row.recommendContactDate())
                .memo(row.memo())
                .hasReply(row.hasReply())
                .repliedAt(row.repliedAt())
                .leadTemperature(row.leadTemperature())
                .priorityScore(row.priorityScore())
                .nonConversionReasons(reasons)
                .nextBestActionTitle(row.nextBestActionTitle())
                .nextBestActionDescription(row.nextBestActionDescription())
                .latestMessageTemplateId(row.latestMessageTemplateId())
                .latestMessageDeliveryStatus(row.latestMessageDeliveryStatus())
                .latestMessageGeneratedAt(row.latestMessageGeneratedAt())
                .build();
    }

    private FollowUpEndedItemResponse toEndedItem(
            EndedRow row,
            List<FollowUpEndedItemResponse.NonConversionReasonInfo> reasons
    ) {
        return FollowUpEndedItemResponse.builder()
                .followUpId(row.followUpId())
                .customerId(row.customerId())
                .customerName(row.customerName())
                .phoneNum(row.phoneNum())
                .gender(Gender.valueOf(row.gender()))
                .serviceName(row.serviceName())
                .customerStatus(CustomerStatus.valueOf(row.customerStatus()))
                .followUpStatus(FollowUpStatus.valueOf(row.followUpStatus()))
                .contactRound(row.contactRound())
                .hasReply(row.hasReply())
                .repliedAt(row.repliedAt())
                .latestMessageTemplateId(row.latestMessageTemplateId())
                .latestMessageDeliveryStatus(row.latestMessageDeliveryStatus())
                .latestContactAt(row.latestContactAt())
                .memo(row.memo())
                .nonConversionReasons(reasons)
                .followUpCompleted(row.followUpCompleted())
                .build();
    }

    private YearMonth resolveMonth(String month) {
        try {
            if (month == null || month.isBlank()) {
                return YearMonth.now(Clock.system(CustomerManagementQueryValidator.SERVICE_ZONE_ID));
            }
            return YearMonth.parse(month, MONTH_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(FollowUpManagementErrorCode.INVALID_INPUT_VALUE);
        }
    }
}

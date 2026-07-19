package com.fitback.domain.event.controller;

import com.fitback.domain.event.dto.request.EventCreateRequest;
import com.fitback.domain.event.dto.request.EventUpdateRequest;
import com.fitback.domain.event.dto.response.EventResponse;
import com.fitback.domain.event.service.EventService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/store/events")
@RequiredArgsConstructor
@Tag(name = "Event", description = "이벤트 API")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "이벤트 목록 조회")
    public ResponseEntity<ApiResponse<List<EventResponse>>> getEvents(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.onSuccess(eventService.getEvents(userId)));
    }

    @PostMapping
    @Operation(summary = "이벤트 등록")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Valid @RequestBody EventCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess("이벤트가 등록되었습니다.", eventService.createEvent(userId, request)));
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "이벤트 수정")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Parameter(description = "Event ID") @PathVariable UUID eventId,
            @Valid @RequestBody EventUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.onSuccess("이벤트가 수정되었습니다.", eventService.updateEvent(userId, eventId, request)));
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "이벤트 삭제")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Parameter(description = "Event ID") @PathVariable UUID eventId
    ) {
        eventService.deleteEvent(userId, eventId);
        return ResponseEntity.ok(ApiResponse.onSuccess("이벤트가 삭제되었습니다.", null));
    }
}

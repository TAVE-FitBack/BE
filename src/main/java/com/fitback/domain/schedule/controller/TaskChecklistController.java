package com.fitback.domain.schedule.controller;

import com.fitback.domain.schedule.dto.request.TaskChecklistUpdateRequest;
import com.fitback.domain.schedule.dto.response.TaskChecklistListResponse;
import com.fitback.domain.schedule.dto.response.TaskChecklistResponse;
import com.fitback.domain.schedule.service.TaskChecklistService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/task-checklists")
@RequiredArgsConstructor
@Tag(name = "Task Checklist", description = "스케줄러 할일 체크리스트 API")
public class TaskChecklistController {

    private final TaskChecklistService taskChecklistService;

    @GetMapping
    @Operation(summary = "일정 체크리스트 조회", description = "선택 날짜의 상담/방문/기타 일정 체크리스트를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 잘못된 날짜"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<ApiResponse<TaskChecklistListResponse>> getTaskChecklists(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "조회할 체크리스트 날짜(YYYY-MM-DD)", example = "2026-10-15")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        TaskChecklistListResponse response = taskChecklistService.getTaskChecklists(storeId, date);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PatchMapping("/{taskId}")
    @Operation(summary = "일정 체크리스트 완료/해제", description = "체크리스트의 완료 또는 해제 상태를 변경합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 잘못된 요청값"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "체크리스트 없음")
    })
    public ResponseEntity<ApiResponse<TaskChecklistResponse>> updateTaskDone(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "완료/해제할 체크리스트 ID")
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskChecklistUpdateRequest request
    ) {
        TaskChecklistResponse response = taskChecklistService.updateTaskDone(
                storeId,
                taskId,
                request.getIsDone()
        );
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}

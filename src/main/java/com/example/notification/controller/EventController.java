package com.example.notification.controller;

import com.example.notification.dto.request.SubmitEventRequest;
import com.example.notification.dto.response.DashboardStatsResponse;
import com.example.notification.dto.response.EventResponse;
import com.example.notification.model.NotificationStatus;
import com.example.notification.security.AppUserDetails;
import com.example.notification.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Submit and manage webhook delivery events")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @Operation(summary = "Submit a webhook event for delivery")
    public ResponseEntity<EventResponse> submit(@Valid @RequestBody SubmitEventRequest request,
                                                 @AuthenticationPrincipal AppUserDetails principal) {
        EventResponse response = eventService.submit(request, principal.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    @Operation(summary = "List events with optional filtering and pagination")
    public ResponseEntity<Page<EventResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long integrationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AppUserDetails principal) {

        NotificationStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = NotificationStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore invalid status filter
            }
        }

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(eventService.listForUser(principal.getId(), statusFilter, integrationId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event details with full delivery history")
    public ResponseEntity<EventResponse> get(@PathVariable Long id,
                                              @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(eventService.getDetail(id, principal.getId()));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Manually retry a FAILED event")
    public ResponseEntity<EventResponse> retry(@PathVariable Long id,
                                                @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(eventService.manualRetry(id, principal.getId()));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard delivery statistics")
    public ResponseEntity<DashboardStatsResponse> stats(@AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(eventService.getDashboardStats(principal.getId()));
    }
}

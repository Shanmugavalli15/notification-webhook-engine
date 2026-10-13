package com.example.notification.controller;

import com.example.notification.model.Notification;
import com.example.notification.service.NotificationService;
import com.example.notification.util.HmacUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy notification controller — preserves the existing dashboard's API.
 * New integrations should use /api/v1/events and /api/v1/integrations.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Legacy Dashboard", description = "Existing notification dashboard endpoints (backward compatible)")
public class NotificationController {

    private final NotificationService notificationService;

    /** Legacy endpoint: used by the existing HTML dashboard */
    @PostMapping("/notifications")
    @Operation(summary = "Submit a notification (legacy dashboard API)")
    public ResponseEntity<Map<String, Object>> createNotification(@RequestBody Notification notification) {
        log.info("Legacy notification submitted: recipient={}, channel={}", notification.getRecipient(), notification.getChannelType());
        Notification created = notificationService.createNotification(notification);

        Map<String, Object> response = new HashMap<>();
        response.put("id", created.getId());
        response.put("status", created.getStatus());
        response.put("message", "Notification accepted for asynchronous delivery");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Legacy endpoint: used by the existing HTML dashboard */
    @GetMapping("/notifications")
    @Operation(summary = "Get all notifications (legacy dashboard API)")
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }

    /**
     * Mock destination for testing webhook delivery.
     * Also demonstrates HMAC signature verification.
     */
    @PostMapping("/mock-destination")
    @Operation(summary = "Mock webhook receiver — simulates success, failure, or timeout")
    public ResponseEntity<Map<String, String>> mockDestination(
            @RequestBody(required = false) String body,
            @RequestParam(name = "fail", required = false, defaultValue = "false") boolean fail,
            @RequestParam(name = "timeout", required = false, defaultValue = "false") boolean timeout,
            @RequestParam(name = "secret", required = false) String secret,
            @RequestHeader(value = "X-HookFlow-Signature", required = false) String signature,
            @RequestHeader(value = "X-HookFlow-Timestamp", required = false) String timestampHeader,
            @RequestHeader(value = "X-HookFlow-Event-Id", required = false) String eventId) {

        log.info("Mock Destination received webhook. eventId={}, fail={}, timeout={}", eventId, fail, timeout);

        // Optional signature verification demo
        if (secret != null && !secret.isBlank() && signature != null && timestampHeader != null) {
            try {
                long ts = Long.parseLong(timestampHeader);
                long age = Instant.now().getEpochSecond() - ts;
                if (Math.abs(age) > 300) {
                    log.warn("Mock Destination: signature timestamp too old ({} seconds)", age);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Timestamp too old", "eventId", String.valueOf(eventId)));
                }
                byte[] bodyBytes = body != null ? body.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
                String expected = HmacUtils.computeSignature(secret, ts, bodyBytes);
                if (!HmacUtils.verifySignature(expected, signature)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Signature mismatch"));
                }
                log.info("Mock Destination: signature verified successfully for event {}", eventId);
            } catch (Exception e) {
                log.warn("Mock Destination: signature verification error: {}", e.getMessage());
            }
        }

        if (timeout || (body != null && body.toLowerCase().contains("timeout"))) {
            try {
                log.info("Simulating long delay/timeout in Mock Destination...");
                Thread.sleep(12000); // Sleep > 10s read timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(Map.of("error", "Gateway Timeout", "message", "Simulated connection timeout"));
        }

        if (fail || (body != null && body.toLowerCase().contains("fail"))) {
            log.warn("Mock Destination returning error response.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal Server Error", "message", "Simulated downstream failure"));
        }

        log.info("Mock Destination returning success response for event {}", eventId);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Webhook event processed successfully",
                "eventId", eventId != null ? eventId : "unknown"));
    }

    /** Health check endpoint */
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "HookFlow"));
    }
}

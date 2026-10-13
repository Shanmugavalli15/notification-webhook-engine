package com.example.notification.controller;

import com.example.notification.model.Notification;
import com.example.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/notifications")
    public ResponseEntity<Map<String, Object>> createNotification(@RequestBody Notification notification) {
        log.info("Received request to create notification: {}", notification);
        Notification created = notificationService.createNotification(notification);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", created.getId());
        response.put("status", created.getStatus());
        response.put("message", "Notification accepted for asynchronous delivery");
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }

    @PostMapping("/mock-destination")
    public ResponseEntity<Map<String, String>> mockDestination(
            @RequestBody(required = false) String body,
            @RequestParam(name = "fail", required = false, defaultValue = "false") boolean fail,
            @RequestParam(name = "timeout", required = false, defaultValue = "false") boolean timeout) {
        
        log.info("Mock Destination received webhook request. fail={}, timeout={}, body={}", fail, timeout, body);

        if (timeout || (body != null && body.toLowerCase().contains("timeout"))) {
            try {
                log.info("Simulating long delay/timeout in Mock Destination...");
                Thread.sleep(5000); // Sleep 5 seconds to exceed 3s read timeout
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

        log.info("Mock Destination returning success response.");
        return ResponseEntity.ok(Map.of("status", "success", "message", "Webhook event processed successfully"));
    }
}

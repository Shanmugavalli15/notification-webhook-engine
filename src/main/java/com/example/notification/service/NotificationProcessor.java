package com.example.notification.service;

import com.example.notification.model.ChannelType;
import com.example.notification.model.DeliveryLog;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.DeliveryLogRepository;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Async("notificationTaskExecutor")
    public void processAsync(Notification notification) {
        log.info("Starting async delivery process for Notification ID: {}", notification.getId());
        
        int attempt = 0;
        boolean delivered = false;

        while (attempt < 4 && !delivered) {
            attempt++;
            
            if (attempt > 1) {
                long delay = getDelayForAttempt(attempt);
                log.info("Attempt {} failed. Waiting {}ms before retrying Notification ID: {}", attempt - 1, delay, notification.getId());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    log.error("Notification process interrupted for ID: {}", notification.getId(), e);
                    Thread.currentThread().interrupt();
                    break;
                }
                
                // Update status to RETRYING
                notification.setStatus(NotificationStatus.RETRYING);
                notification.setRetryCount(attempt - 1);
                notification = notificationRepository.saveAndFlush(notification);
            }

            DeliveryResult result = performDelivery(notification, attempt);

            // Log the attempt
            DeliveryLog deliveryLog = DeliveryLog.builder()
                    .notification(notification)
                    .httpStatusCode(result.statusCode)
                    .responseBody(result.body)
                    .attemptNumber(attempt)
                    .build();
            deliveryLogRepository.saveAndFlush(deliveryLog);

            if (result.success) {
                delivered = true;
                notification.setStatus(NotificationStatus.DELIVERED);
                notification.setRetryCount(attempt - 1);
                notificationRepository.saveAndFlush(notification);
                log.info("Notification ID: {} delivered successfully on attempt {}", notification.getId(), attempt);
            } else {
                if (attempt == 4) {
                    notification.setStatus(NotificationStatus.FAILED_DLQ);
                    notification.setRetryCount(3);
                    notificationRepository.saveAndFlush(notification);
                    log.warn("Notification ID: {} failed all retry attempts. Moved to FAILED_DLQ.", notification.getId());
                } else {
                    notification.setStatus(NotificationStatus.RETRYING);
                    notification.setRetryCount(attempt);
                    notificationRepository.saveAndFlush(notification);
                }
            }
        }
    }

    private long getDelayForAttempt(int attempt) {
        // Attempt transitions:
        // Attempt 1 -> (fail) -> retry 1 (Attempt 2) has delay 2s
        // Attempt 2 -> (fail) -> retry 2 (Attempt 3) has delay 5s
        // Attempt 3 -> (fail) -> retry 3 (Attempt 4) has delay 10s
        switch (attempt) {
            case 2: return 2000;
            case 3: return 5000;
            case 4: return 10000;
            default: return 0;
        }
    }

    private DeliveryResult performDelivery(Notification notification, int attempt) {
        String recipient = notification.getRecipient();
        ChannelType channel = notification.getChannelType();
        String payload = notification.getPayload();

        boolean isUrl = recipient.startsWith("http://") || recipient.startsWith("https://");

        if (channel == ChannelType.WEBHOOK || isUrl) {
            if (!isUrl) {
                return new DeliveryResult(false, 400, "Invalid URL format for Webhook destination");
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(recipient))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .timeout(Duration.ofSeconds(3))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                boolean success = (status >= 200 && status < 300);
                return new DeliveryResult(success, status, response.body());
            } catch (Exception e) {
                log.error("HTTP execution error on attempt {} for Notification ID: {}", attempt, notification.getId(), e);
                return new DeliveryResult(false, 504, "HTTP request failed: " + e.getMessage());
            }
        } else {
            // Simulated channels (EMAIL / SMS)
            if (recipient.toLowerCase().contains("fail")) {
                return new DeliveryResult(false, 500, "Simulated downstream gateway failure (Internal Server Error)");
            } else if (recipient.toLowerCase().contains("timeout")) {
                return new DeliveryResult(false, 504, "Simulated gateway request timeout (Connection Timeout)");
            } else {
                return new DeliveryResult(true, 200, "Simulated transmission successful: Mock gateway returned 200 OK");
            }
        }
    }

    private static class DeliveryResult {
        final boolean success;
        final int statusCode;
        final String body;

        DeliveryResult(boolean success, int statusCode, String body) {
            this.success = success;
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}

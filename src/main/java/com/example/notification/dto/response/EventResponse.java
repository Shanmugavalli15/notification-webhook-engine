package com.example.notification.dto.response;

import com.example.notification.model.Notification;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EventResponse {
    private Long id;
    private String eventType;
    private String status;
    private String payload;
    private int retryCount;
    private int maxAttempts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lastAttemptAt;
    private String errorMessage;
    private Long integrationId;
    private String integrationName;
    private List<DeliveryAttemptResponse> deliveryLogs;

    public static EventResponse from(Notification n) {
        return EventResponse.builder()
                .id(n.getId())
                .eventType(n.getEventType())
                .status(n.getStatus() != null ? n.getStatus().name() : null)
                .payload(n.getPayload())
                .retryCount(n.getRetryCount())
                .maxAttempts(n.getMaxAttempts())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .nextRetryAt(n.getNextRetryAt())
                .lastAttemptAt(n.getLastAttemptAt())
                .errorMessage(n.getErrorMessage())
                .integrationId(n.getIntegration() != null ? n.getIntegration().getId() : null)
                .integrationName(n.getIntegration() != null ? n.getIntegration().getName() : null)
                .build();
    }

    public static EventResponse fromWithLogs(Notification n) {
        EventResponse resp = from(n);
        if (n.getDeliveryLogs() != null) {
            resp.setDeliveryLogs(n.getDeliveryLogs().stream()
                    .map(DeliveryAttemptResponse::from)
                    .toList());
        }
        return resp;
    }
}

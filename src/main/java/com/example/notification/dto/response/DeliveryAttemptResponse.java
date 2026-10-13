package com.example.notification.dto.response;

import com.example.notification.model.DeliveryLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeliveryAttemptResponse {
    private Long id;
    private int attemptNumber;
    private Integer httpStatusCode;
    private Long durationMs;
    private boolean success;
    private String responseBody;
    private String errorMessage;
    private LocalDateTime timestamp;

    public static DeliveryAttemptResponse from(DeliveryLog log) {
        return DeliveryAttemptResponse.builder()
                .id(log.getId())
                .attemptNumber(log.getAttemptNumber())
                .httpStatusCode(log.getHttpStatusCode())
                .durationMs(log.getDurationMs())
                .success(log.isSuccess())
                .responseBody(truncate(log.getResponseBody(), 500))
                .errorMessage(log.getErrorMessage())
                .timestamp(log.getTimestamp())
                .build();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}

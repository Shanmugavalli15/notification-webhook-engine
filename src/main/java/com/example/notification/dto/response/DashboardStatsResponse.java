package com.example.notification.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsResponse {
    private long totalEvents;
    private long successfulDeliveries;
    private long failedDeliveries;
    private long pendingEvents;
    private long retryingEvents;
    private double successRate;
    private Double avgDeliveryDurationMs;
}

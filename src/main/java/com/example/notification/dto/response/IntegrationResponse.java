package com.example.notification.dto.response;

import com.example.notification.model.Integration;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class IntegrationResponse {
    private Long id;
    private String name;
    private String destinationUrl;
    private String eventTypes;
    private boolean enabled;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Indicates that a signing secret exists — never exposes the actual secret value. */
    private boolean hasSigningSecret;

    public static IntegrationResponse from(Integration i) {
        return IntegrationResponse.builder()
                .id(i.getId())
                .name(i.getName())
                .destinationUrl(i.getDestinationUrl())
                .eventTypes(i.getEventTypes())
                .enabled(i.isEnabled())
                .description(i.getDescription())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .hasSigningSecret(i.getSigningSecret() != null && !i.getSigningSecret().isBlank())
                .build();
    }
}

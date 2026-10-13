package com.example.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitEventRequest {

    /** Target integration ID. */
    private Long integrationId;

    @NotBlank(message = "Event type is required")
    @Size(max = 100)
    private String eventType;

    @NotBlank(message = "Payload is required")
    @Size(max = 1_048_576, message = "Payload may not exceed 1 MB")
    private String payload;

    /** Optional idempotency key — duplicate submissions with same key are safe. */
    @Size(max = 255)
    private String idempotencyKey;
}

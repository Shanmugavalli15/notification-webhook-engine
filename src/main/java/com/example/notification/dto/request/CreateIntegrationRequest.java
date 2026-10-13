package com.example.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateIntegrationRequest {

    @NotBlank(message = "Integration name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Destination URL is required")
    @Size(max = 2048)
    private String destinationUrl;

    @Size(max = 1000)
    private String eventTypes = "*";

    @Size(max = 1000)
    private String description;

    private boolean enabled = true;
}

package com.example.notification.controller;

import com.example.notification.dto.request.CreateIntegrationRequest;
import com.example.notification.dto.response.IntegrationResponse;
import com.example.notification.security.AppUserDetails;
import com.example.notification.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@Tag(name = "Integrations", description = "Manage webhook integrations")
@SecurityRequirement(name = "bearerAuth")
public class IntegrationController {

    private final IntegrationService integrationService;

    @GetMapping
    @Operation(summary = "List all integrations for the authenticated user")
    public ResponseEntity<List<IntegrationResponse>> list(@AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(integrationService.listForUser(principal.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get integration details")
    public ResponseEntity<IntegrationResponse> get(@PathVariable Long id,
                                                    @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(integrationService.getForUser(id, principal.getId()));
    }

    @PostMapping
    @Operation(summary = "Create a new integration")
    public ResponseEntity<IntegrationResponse> create(@Valid @RequestBody CreateIntegrationRequest request,
                                                       @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(integrationService.create(request, principal.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an integration")
    public ResponseEntity<IntegrationResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody CreateIntegrationRequest request,
                                                       @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(integrationService.update(id, request, principal.getId()));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Enable or disable an integration")
    public ResponseEntity<Void> toggle(@PathVariable Long id,
                                        @RequestParam boolean enabled,
                                        @AuthenticationPrincipal AppUserDetails principal) {
        integrationService.toggleEnabled(id, principal.getId(), enabled);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate-secret")
    @Operation(summary = "Rotate the signing secret — returns the new secret once")
    public ResponseEntity<Map<String, String>> rotateSecret(@PathVariable Long id,
                                                             @AuthenticationPrincipal AppUserDetails principal) {
        return ResponseEntity.ok(integrationService.rotateSecret(id, principal.getId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an integration")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @AuthenticationPrincipal AppUserDetails principal) {
        integrationService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}

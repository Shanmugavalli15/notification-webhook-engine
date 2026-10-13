package com.example.notification.service;

import com.example.notification.dto.request.CreateIntegrationRequest;
import com.example.notification.dto.response.IntegrationResponse;
import com.example.notification.model.AppUser;
import com.example.notification.model.Integration;
import com.example.notification.repository.AppUserRepository;
import com.example.notification.repository.IntegrationRepository;
import com.example.notification.util.HmacUtils;
import com.example.notification.util.UrlSafetyChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final AppUserRepository userRepository;

    @Value("${hookflow.allow-localhost:true}")
    private boolean allowLocalhost;

    @Transactional(readOnly = true)
    public List<IntegrationResponse> listForUser(Long ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return integrationRepository.findByOwner_IdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(IntegrationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationResponse getForUser(Long id, Long ownerId) {
        if (id == null || ownerId == null) {
            throw new NoSuchElementException("Integration not found");
        }
        Integration integration = integrationRepository.findByIdAndOwner_Id(id, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Integration not found"));
        return IntegrationResponse.from(integration);
    }

    @Transactional
    public IntegrationResponse create(CreateIntegrationRequest request, Long ownerId) {
        if (request == null) {
            throw new IllegalArgumentException("Integration request must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID must not be null");
        }

        UrlSafetyChecker.validate(request.getDestinationUrl(), allowLocalhost);

        AppUser owner = userRepository.getReferenceById(Objects.requireNonNull(ownerId));
        String secret = HmacUtils.generateSecret();

        Integration integration = Integration.builder()
                .owner(owner)
                .name(request.getName())
                .destinationUrl(request.getDestinationUrl())
                .signingSecret(secret)
                .eventTypes(request.getEventTypes() != null ? request.getEventTypes() : "*")
                .description(request.getDescription())
                .enabled(request.isEnabled())
                .build();

        integration = integrationRepository.save(Objects.requireNonNull(integration));
        log.info("Integration '{}' (id={}) created for user {}", integration.getName(), integration.getId(), ownerId);
        return IntegrationResponse.from(integration);
    }

    @Transactional
    public IntegrationResponse update(Long id, CreateIntegrationRequest request, Long ownerId) {
        if (id == null || ownerId == null) {
            throw new NoSuchElementException("Integration not found");
        }
        if (request == null) {
            throw new IllegalArgumentException("Integration request must not be null");
        }

        Integration integration = integrationRepository.findByIdAndOwner_Id(id, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Integration not found"));

        UrlSafetyChecker.validate(request.getDestinationUrl(), allowLocalhost);

        integration.setName(request.getName());
        integration.setDestinationUrl(request.getDestinationUrl());
        if (request.getEventTypes() != null) {
            integration.setEventTypes(request.getEventTypes());
        }
        integration.setDescription(request.getDescription());
        integration.setEnabled(request.isEnabled());

        return IntegrationResponse.from(integrationRepository.save(Objects.requireNonNull(integration)));
    }

    @Transactional
    public void toggleEnabled(Long id, Long ownerId, boolean enabled) {
        if (id == null || ownerId == null) {
            throw new NoSuchElementException("Integration not found");
        }
        Integration integration = integrationRepository.findByIdAndOwner_Id(id, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Integration not found"));
        integration.setEnabled(enabled);
        integrationRepository.save(Objects.requireNonNull(integration));
    }

    @Transactional
    public Map<String, String> rotateSecret(Long id, Long ownerId) {
        if (id == null || ownerId == null) {
            throw new NoSuchElementException("Integration not found");
        }
        Integration integration = integrationRepository.findByIdAndOwner_Id(id, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Integration not found"));

        String newSecret = HmacUtils.generateSecret();
        integration.setSigningSecret(newSecret);
        integrationRepository.save(Objects.requireNonNull(integration));

        log.info("Signing secret rotated for integration {} (owner={})", id, ownerId);
        return Map.of("signingSecret", newSecret,
                "message", "Store this secret securely — it will not be shown again");
    }

    @Transactional
    public void delete(Long id, Long ownerId) {
        if (id == null || ownerId == null) {
            throw new NoSuchElementException("Integration not found");
        }
        Integration integration = integrationRepository.findByIdAndOwner_Id(id, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Integration not found"));
        integrationRepository.delete(Objects.requireNonNull(integration));
        log.info("Integration {} deleted by user {}", id, ownerId);
    }

    /** Internal method — returns the full entity with signing secret for delivery. */
    @Transactional(readOnly = true)
    public Integration getEntityById(Long id) {
        if (id == null) {
            throw new NoSuchElementException("Integration not found: null");
        }
        return integrationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Integration not found: " + id));
    }
}

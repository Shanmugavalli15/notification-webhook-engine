package com.example.notification.service;

import com.example.notification.model.DeliveryLog;
import com.example.notification.model.Integration;
import com.example.notification.model.Notification;
import com.example.notification.model.NotificationStatus;
import com.example.notification.repository.DeliveryLogRepository;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.util.HmacUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Performs webhook delivery attempts.
 *
 * Design principles:
 * - Only an event ID is passed in — entities are reloaded in fresh transactions.
 * - No DB transaction is held open during the outbound HTTP call.
 * - @Transactional methods are public (Spring proxy requirement).
 * - On failure: schedules next retry via exponential backoff + jitter.
 *
 * State transitions:
 *   PENDING/RETRYING → [claim] → PROCESSING → [HTTP] → SUCCESSFUL | RETRYING | FAILED
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final DeliveryLogRepository deliveryLogRepository;

    @Value("${hookflow.allow-localhost:true}")
    private boolean allowLocalhost;

    @Value("${hookflow.retry.base-delay-ms:2000}")
    private long baseDelayMs;

    @Value("${hookflow.retry.max-delay-ms:60000}")
    private long maxDelayMs;

    @Value("${hookflow.retry.jitter-factor:0.25}")
    private double jitterFactor;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final Random random = new Random();

    // ─── Public entry points ────────────────────────────────────────────────

    /** Async entry point — called after event is persisted. */
    @Async("notificationTaskExecutor")
    public void processAsync(Long eventId) {
        if (eventId == null) {
            log.warn("processAsync called with null eventId");
            return;
        }
        log.info("[event={}] Async delivery dispatched", eventId);
        attemptDelivery(eventId);
    }

    /** Synchronous entry point — used by scheduler and startup recovery. */
    public void attemptDelivery(Long eventId) {
        if (eventId == null) {
            log.warn("attemptDelivery called with null eventId");
            return;
        }

        // Step 1: claim the event in its own transaction
        Optional<Notification> claimed = claimEvent(eventId);
        if (claimed.isEmpty()) {
            log.debug("[event={}] Cannot claim — already processing or in terminal state", eventId);
            return;
        }

        Notification event = claimed.get();
        int attemptNumber = event.getRetryCount() + 1;
        log.info("[event={}] Attempting delivery #{}/{}", eventId, attemptNumber, event.getMaxAttempts());

        // Step 2: HTTP call — NO DB transaction open
        DeliveryResult result = performHttpDelivery(event, attemptNumber);

        // Step 3: persist result in its own transaction
        persistResult(eventId, attemptNumber, result);
    }

    // ─── Transactional operations (public — required for Spring proxy) ──────

    /** Claims the event by transitioning PENDING/RETRYING → PROCESSING. */
    @Transactional
    public Optional<Notification> claimEvent(Long eventId) {
        if (eventId == null) {
            return Optional.empty();
        }

        Optional<Notification> opt = notificationRepository.findByIdWithIntegration(eventId);
        if (opt.isEmpty()) return Optional.empty();

        Notification event = opt.get();
        NotificationStatus s = event.getStatus();

        if (s == NotificationStatus.PENDING || s == NotificationStatus.RETRYING) {
            event.setStatus(NotificationStatus.PROCESSING);
            event.setLastAttemptAt(LocalDateTime.now());
            notificationRepository.save(Objects.requireNonNull(event));
            return Optional.of(event);
        }
        return Optional.empty();
    }

    /** Persists the delivery result and schedules next retry if needed. */
    @Transactional
    public void persistResult(Long eventId, int attemptNumber, DeliveryResult result) {
        if (eventId == null || result == null) {
            log.warn("persistResult called with null eventId or result");
            return;
        }

        Notification event = notificationRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found after delivery: " + eventId));

        DeliveryLog deliveryLog = DeliveryLog.builder()
                .notification(event)
                .attemptNumber(attemptNumber)
                .httpStatusCode(result.httpStatusCode())
                .durationMs(result.durationMs())
                .responseBody(result.responseBody())
                .errorMessage(result.errorMessage())
                .success(result.success())
                .build();
        deliveryLogRepository.save(Objects.requireNonNull(deliveryLog));

        if (result.success()) {
            event.setStatus(NotificationStatus.SUCCESSFUL);
            event.setRetryCount(attemptNumber);
            event.setNextRetryAt(null);
            event.setErrorMessage(null);
            log.info("[event={}] SUCCESSFUL on attempt #{} — HTTP {} in {}ms",
                    eventId, attemptNumber, result.httpStatusCode(), result.durationMs());
        } else {
            event.setErrorMessage(sanitizeError(result.errorMessage(), result.httpStatusCode()));
            event.setRetryCount(attemptNumber);

            boolean permanentFailure = isPermanentFailure(result.httpStatusCode());
            boolean exhausted = attemptNumber >= event.getMaxAttempts();

            if (permanentFailure || exhausted) {
                event.setStatus(NotificationStatus.FAILED);
                event.setNextRetryAt(null);
                log.warn("[event={}] FAILED permanently after {} attempt(s) — permanent={}, exhausted={}",
                        eventId, attemptNumber, permanentFailure, exhausted);
            } else {
                long delayMs = computeBackoff(attemptNumber);
                event.setStatus(NotificationStatus.RETRYING);
                event.setNextRetryAt(LocalDateTime.now().plusNanos(delayMs * 1_000_000L));
                log.info("[event={}] RETRYING — next attempt #{} scheduled in {}ms",
                        eventId, attemptNumber + 1, delayMs);
            }
        }
        notificationRepository.save(Objects.requireNonNull(event));
    }

    // ─── Private helpers ───────────────────────────────────────────────────

    private DeliveryResult performHttpDelivery(Notification event, int attemptNumber) {
        if (event == null) {
            return DeliveryResult.error("Event is null");
        }

        String url;
        String signingSecret;

        Integration integration = event.getIntegration();
        if (integration != null) {
            url = integration.getDestinationUrl();
            signingSecret = integration.getSigningSecret();
        } else {
            url = event.getRecipient();
            signingSecret = null;
        }

        if (url == null || url.isBlank() || "direct".equalsIgnoreCase(url)) {
            return DeliveryResult.error("No destination URL configured");
        }

        String payload = event.getPayload() != null ? event.getPayload() : "";
        byte[] bodyBytes = payload.getBytes(StandardCharsets.UTF_8);
        long timestampEpoch = Instant.now().getEpochSecond();
        String eventType = event.getEventType() != null ? event.getEventType() : "default";

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "HookFlow/1.0")
                    .header("X-HookFlow-Event-Id", String.valueOf(event.getId()))
                    .header("X-HookFlow-Event-Type", eventType)
                    .header("X-HookFlow-Attempt", String.valueOf(attemptNumber))
                    .header("X-HookFlow-Timestamp", String.valueOf(timestampEpoch))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .timeout(Duration.ofSeconds(10));

            if (signingSecret != null && !signingSecret.isBlank()) {
                String sig = HmacUtils.computeSignature(signingSecret, timestampEpoch, bodyBytes);
                reqBuilder.header("X-HookFlow-Signature", sig);
            }

            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - start;

            int status = response.statusCode();
            boolean success = status >= 200 && status < 300;
            String rawBody = response.body();
            String body = rawBody != null ? truncate(rawBody, 2000) : "";

            log.debug("[event={}] HTTP {} from {} in {}ms (attempt #{})",
                    event.getId(), status, url, durationMs, attemptNumber);
            return new DeliveryResult(success, status, durationMs, body, success ? null : "HTTP " + status);

        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("[event={}] Timeout on attempt #{}: {}", event.getId(), attemptNumber, e.getMessage());
            return DeliveryResult.error("Request timed out");
        } catch (Exception e) {
            log.error("[event={}] Delivery error on attempt #{}: {} — {}",
                    event.getId(), attemptNumber, e.getClass().getSimpleName(), e.getMessage());
            return DeliveryResult.error("Connection error: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Exponential backoff with optional jitter.
     * delay = clamp(base * 2^(attempt-1) * (1 ± jitter), 1s, maxDelay)
     */
    private long computeBackoff(int attempt) {
        double exp = baseDelayMs * Math.pow(2, attempt - 1);
        double jitter = jitterFactor > 0 ? exp * jitterFactor * (random.nextDouble() * 2 - 1) : 0;
        return (long) Math.max(1000, Math.min(exp + jitter, maxDelayMs));
    }

    /**
     * Permanent failures: 4xx except 408 (timeout) and 429 (rate-limit).
     * 5xx and null (network error) are transient.
     */
    private boolean isPermanentFailure(Integer statusCode) {
        if (statusCode == null) return false;
        if (statusCode == 408 || statusCode == 429) return false;
        return statusCode >= 400 && statusCode < 500;
    }

    private String sanitizeError(String msg, Integer statusCode) {
        if (msg != null && !msg.isBlank()) return truncate(msg, 500);
        if (statusCode != null) return "HTTP " + statusCode;
        return "Delivery failed";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "\u2026" : s;
    }

    /**
     * Immutable delivery result record.
     */
    public record DeliveryResult(boolean success, Integer httpStatusCode, Long durationMs,
                                  String responseBody, String errorMessage) {
        public static DeliveryResult error(String msg) {
            return new DeliveryResult(false, null, null, null, msg);
        }
    }
}

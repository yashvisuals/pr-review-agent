package com.yash.prreview.presentation.webhook;

import com.yash.prreview.application.event.PrReceivedEvent;
import com.yash.prreview.domain.model.PullRequest;
import com.yash.prreview.infrastructure.github.WebhookSignatureVerifier;
import com.yash.prreview.infrastructure.github.dto.WebhookPayload;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Receives and validates GitHub webhook events for pull requests.
 *
 * Design: returns 200 immediately after signature validation.
 * Actual review processing is asynchronous via Spring Events + virtual threads.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);
    private static final Set<String> REVIEWABLE_ACTIONS = Set.of("opened", "reopened", "synchronize");

    private final WebhookSignatureVerifier signatureVerifier;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public GitHubWebhookController(WebhookSignatureVerifier signatureVerifier,
                                    ApplicationEventPublisher eventPublisher,
                                    MeterRegistry meterRegistry,
                                    ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/github")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "unknown") String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", defaultValue = "unknown") String deliveryId,
            @RequestBody String rawPayload) {

        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, WebhookPayload.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON payload"));
        }

        meterRegistry.counter("webhook.received", "event", eventType).increment();

        // Security: verify HMAC-SHA256 signature before processing
        if (!signatureVerifier.verify(rawPayload, signature)) {
            log.warn("Webhook signature verification failed for delivery: {}", deliveryId);
            meterRegistry.counter("webhook.rejected", "reason", "invalid_signature").increment();
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        log.debug("Received {} event, delivery: {}", eventType, deliveryId);

        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "not a PR event"));
        }

        if (payload.pullRequest() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing pull_request in payload"));
        }

        String action = payload.action();
        if (!REVIEWABLE_ACTIONS.contains(action)) {
            log.debug("Ignoring PR action: {}", action);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "action not reviewable"));
        }

        PullRequest pullRequest = mapToDomain(payload);
        String installationId = payload.installation() != null
                ? String.valueOf(payload.installation().id())
                : null;

        eventPublisher.publishEvent(new PrReceivedEvent(pullRequest, action, installationId));

        log.info("Queued review for PR #{} in {} (action: {})",
                pullRequest.number(), pullRequest.repositoryFullName(), action);

        meterRegistry.counter("webhook.processed", "action", action).increment();

        return ResponseEntity.accepted()
                .body(Map.of(
                        "status", "accepted",
                        "message", "Review queued for PR #" + pullRequest.number(),
                        "deliveryId", deliveryId
                ));
    }

    private PullRequest mapToDomain(WebhookPayload payload) {
        var pr = payload.pullRequest();
        return new PullRequest(
                pr.id(),
                pr.number(),
                pr.title(),
                pr.body(),
                payload.repository().fullName(),
                pr.user().login(),
                pr.head().ref(),
                pr.base().ref(),
                pr.head().sha(),
                List.of(), // files fetched separately during review
                Instant.now(),
                PullRequest.PrState.OPEN
        );
    }
}

package com.yash.prreview.application.usecase;

import com.yash.prreview.application.event.PrReceivedEvent;
import com.yash.prreview.application.event.ReviewCompletedEvent;
import com.yash.prreview.domain.model.PullRequest;
import com.yash.prreview.domain.model.ReviewResult;
import com.yash.prreview.domain.port.GitHubPort;
import com.yash.prreview.domain.port.ReviewPersistencePort;
import com.yash.prreview.domain.service.ReviewOrchestrationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Application use case: processes an incoming GitHub PR webhook event.
 *
 * Flow: webhook received → validate → fetch PR details → AI review → persist → post to GitHub
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessWebhookUseCase {

    private final ReviewOrchestrationService orchestrationService;
    private final GitHubPort gitHubPort;
    private final ReviewPersistencePort persistencePort;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    /**
     * Handles incoming PR events asynchronously.
     * The webhook controller returns 200 immediately; review happens in background.
     */
    @Async
    @EventListener(PrReceivedEvent.class)
    public void handle(PrReceivedEvent event) {
        PullRequest pr = event.pullRequest();
        log.info("Processing PR #{} for repo {}", pr.number(), pr.repositoryFullName());

        Timer.Sample timerSample = Timer.start(meterRegistry);

        try {
            ReviewResult result = orchestrationService.reviewPullRequest(pr);
            persistencePort.save(result);
            eventPublisher.publishEvent(new ReviewCompletedEvent(result));

            timerSample.stop(meterRegistry.timer("pr.review.duration",
                    "repository", pr.repositoryFullName(),
                    "verdict", result.verdict().name()));

            meterRegistry.counter("pr.review.completed",
                    "verdict", result.verdict().name()).increment();

        } catch (Exception e) {
            log.error("Failed to review PR #{} in {}: {}", pr.number(), pr.repositoryFullName(), e.getMessage(), e);
            meterRegistry.counter("pr.review.failed",
                    "repository", pr.repositoryFullName()).increment();
        }
    }

    /**
     * Posts the completed review back to GitHub.
     */
    @Async
    @EventListener(ReviewCompletedEvent.class)
    public void postReviewToGitHub(ReviewCompletedEvent event) {
        ReviewResult result = event.result();
        String[] parts = result.repositoryFullName().split("/");
        if (parts.length != 2) {
            log.error("Invalid repository name: {}", result.repositoryFullName());
            return;
        }

        String owner = parts[0];
        String repo = parts[1];

        try {
            gitHubPort.submitReview(owner, repo, result.pullRequestNumber(), result);
            log.info("Successfully posted review for PR #{} in {}", result.pullRequestNumber(), result.repositoryFullName());
        } catch (Exception e) {
            log.error("Failed to post review to GitHub for PR #{}: {}", result.pullRequestNumber(), e.getMessage(), e);
        }
    }
}

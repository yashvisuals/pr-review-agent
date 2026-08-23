package com.yash.prreview.application.usecase;

import com.yash.prreview.application.event.ReviewCompletedEvent;
import com.yash.prreview.domain.model.PullRequest;
import com.yash.prreview.domain.model.ReviewResult;
import com.yash.prreview.domain.port.GitHubPort;
import com.yash.prreview.domain.port.ReviewPersistencePort;
import com.yash.prreview.domain.service.ReviewOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Use case for manually triggering a review via REST API (without a webhook).
 * Useful for re-reviewing PRs or triggering from CI pipelines.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerManualReviewUseCase {

    private final GitHubPort gitHubPort;
    private final ReviewOrchestrationService orchestrationService;
    private final ReviewPersistencePort persistencePort;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewResult execute(String owner, String repo, int prNumber) {
        log.info("Manual review triggered for {}/{} PR #{}", owner, repo, prNumber);

        PullRequest pullRequest = gitHubPort.fetchPullRequest(owner, repo, prNumber);
        ReviewResult result = orchestrationService.reviewPullRequest(pullRequest);
        persistencePort.save(result);
        eventPublisher.publishEvent(new ReviewCompletedEvent(result));

        return result;
    }
}

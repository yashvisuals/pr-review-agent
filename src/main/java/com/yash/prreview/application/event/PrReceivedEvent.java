package com.yash.prreview.application.event;

import com.yash.prreview.domain.model.PullRequest;

/**
 * Spring application event fired when a PR webhook is received and validated.
 */
public record PrReceivedEvent(
        PullRequest pullRequest,
        String webhookAction,
        String installationId
) {}

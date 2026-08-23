package com.yash.prreview.application.event;

import com.yash.prreview.domain.model.ReviewResult;

/**
 * Spring application event fired after AI review completes.
 * Triggers GitHub comment posting asynchronously.
 */
public record ReviewCompletedEvent(ReviewResult result) {}

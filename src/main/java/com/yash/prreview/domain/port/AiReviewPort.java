package com.yash.prreview.domain.port;

import com.yash.prreview.domain.model.PullRequest;
import com.yash.prreview.domain.model.ReviewCategory;
import com.yash.prreview.domain.model.ReviewComment;

import java.util.List;

/**
 * Output port (driven adapter) for AI model interactions.
 * Abstraction allows swapping Ollama → Gemini → any model without domain changes.
 */
public interface AiReviewPort {

    /**
     * Analyze a single file's diff for a specific category of issues.
     * Called in parallel across categories using virtual threads.
     */
    List<ReviewComment> analyzeFile(PullRequest.ChangedFile file, ReviewCategory category);

    /**
     * Generate a high-level summary of the entire PR.
     */
    String generatePrSummary(PullRequest pullRequest, List<ReviewComment> allComments);

    /**
     * Calculate a quality score (0-10) for the PR.
     */
    int calculateQualityScore(PullRequest pullRequest, List<ReviewComment> comments);
}

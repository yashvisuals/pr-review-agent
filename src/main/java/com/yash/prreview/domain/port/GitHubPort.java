package com.yash.prreview.domain.port;

import com.yash.prreview.domain.model.PullRequest;
import com.yash.prreview.domain.model.ReviewResult;

/**
 * Output port (driven adapter) for GitHub API interactions.
 * Follows hexagonal architecture — domain doesn't depend on GitHub SDK.
 */
public interface GitHubPort {

    PullRequest fetchPullRequest(String owner, String repo, int prNumber);

    void submitReview(String owner, String repo, int prNumber, ReviewResult result);

    void createCheckRun(String owner, String repo, String headSha, ReviewResult result);

    void updateCheckRun(String owner, String repo, String checkRunId, ReviewResult result);
}

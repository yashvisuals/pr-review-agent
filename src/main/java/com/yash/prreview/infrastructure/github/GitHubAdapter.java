package com.yash.prreview.infrastructure.github;

import com.yash.prreview.domain.model.PullRequest;
import com.yash.prreview.domain.model.ReviewComment;
import com.yash.prreview.domain.model.ReviewResult;
import com.yash.prreview.domain.port.GitHubPort;
import com.yash.prreview.infrastructure.github.dto.GitHubFileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * GitHub REST API adapter implementing the GitHubPort.
 * Uses Spring WebFlux WebClient for non-blocking HTTP calls.
 * Implements retry logic for transient failures.
 */
@Component
public class GitHubAdapter implements GitHubPort {

    private static final Logger log = LoggerFactory.getLogger(GitHubAdapter.class);

    private final WebClient webClient;

    @Value("${github.review.max-files-per-pr:50}")
    private int maxFilesPerPr;

    public GitHubAdapter(WebClient githubWebClient) {
        this.webClient = githubWebClient;
    }

    @Override
    @Retryable(retryFor = WebClientResponseException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public PullRequest fetchPullRequest(String owner, String repo, int prNumber) {
        log.debug("Fetching PR #{} from {}/{}", prNumber, owner, repo);

        // Fetch PR metadata
        var prData = webClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{prNumber}", owner, repo, prNumber)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        // Fetch changed files
        GitHubFileDto[] files = webClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{prNumber}/files?per_page={perPage}",
                        owner, repo, prNumber, maxFilesPerPr)
                .retrieve()
                .bodyToMono(GitHubFileDto[].class)
                .block();

        return mapToDomain(prData, files != null ? Arrays.asList(files) : List.of(), owner + "/" + repo);
    }

    @Override
    @Retryable(retryFor = WebClientResponseException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public void submitReview(String owner, String repo, int prNumber, ReviewResult result) {
        log.info("Submitting review for PR #{} in {}/{}", prNumber, owner, repo);

        List<Map<String, Object>> reviewComments = result.comments().stream()
                .filter(c -> c.lineNumber() > 0)
                .map(this::toGitHubComment)
                .toList();

        // Use COMMENT instead of APPROVE — APPROVE is blocked on self-owned PRs
        String event = switch (result.verdict()) {
            case APPROVE -> "COMMENT";
            case REQUEST_CHANGES -> result.comments().isEmpty() ? "COMMENT" : "REQUEST_CHANGES";
            case COMMENT -> "COMMENT";
        };

        Map<String, Object> reviewBody = Map.of(
                "body", formatReviewBody(result),
                "event", event,
                "comments", reviewComments
        );

        webClient.post()
                .uri("/repos/{owner}/{repo}/pulls/{prNumber}/reviews", owner, repo, prNumber)
                .bodyValue(reviewBody)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnSuccess(r -> log.info("Review submitted successfully for PR #{}", prNumber))
                .doOnError(e -> log.error("Failed to submit review: {}", e.getMessage()))
                .block();
    }

    @Override
    public void createCheckRun(String owner, String repo, String headSha, ReviewResult result) {
        log.debug("Creating check run for {}/{} sha={}", owner, repo, headSha);

        Map<String, Object> checkRun = Map.of(
                "name", "AI Code Review",
                "head_sha", headSha,
                "status", "in_progress",
                "started_at", Instant.now().toString()
        );

        webClient.post()
                .uri("/repos/{owner}/{repo}/check-runs", owner, repo)
                .bodyValue(checkRun)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @Override
    public void updateCheckRun(String owner, String repo, String checkRunId, ReviewResult result) {
        String conclusion = switch (result.verdict()) {
            case APPROVE -> "success";
            case REQUEST_CHANGES -> "failure";
            case COMMENT -> "neutral";
        };

        Map<String, Object> update = Map.of(
                "status", "completed",
                "conclusion", conclusion,
                "completed_at", Instant.now().toString(),
                "output", Map.of(
                        "title", "AI Code Review — Score: " + result.qualityScore() + "/10",
                        "summary", result.summary(),
                        "text", buildCheckRunDetails(result)
                )
        );

        webClient.patch()
                .uri("/repos/{owner}/{repo}/check-runs/{checkRunId}", owner, repo, checkRunId)
                .bodyValue(update)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private PullRequest mapToDomain(Map<?, ?> prData, List<GitHubFileDto> files, String repoFullName) {
        var head = (Map<?, ?>) prData.get("head");
        var base = (Map<?, ?>) prData.get("base");
        var user = (Map<?, ?>) prData.get("user");

        List<PullRequest.ChangedFile> changedFiles = files.stream()
                .map(f -> new PullRequest.ChangedFile(
                        f.filename(), f.status(), f.additions(), f.deletions(), f.patch()))
                .toList();

        return new PullRequest(
                ((Number) prData.get("id")).longValue(),
                ((Number) prData.get("number")).intValue(),
                (String) prData.get("title"),
                (String) prData.get("body"),
                repoFullName,
                (String) user.get("login"),
                (String) head.get("ref"),
                (String) base.get("ref"),
                (String) head.get("sha"),
                changedFiles,
                Instant.now(),
                PullRequest.PrState.OPEN
        );
    }

    private Map<String, Object> toGitHubComment(ReviewComment comment) {
        return Map.of(
                "path", comment.filename(),
                "line", comment.lineNumber(),
                "side", "RIGHT",
                "body", comment.toMarkdown()
        );
    }

    private String formatReviewBody(ReviewResult result) {
        var sb = new StringBuilder();
        sb.append("## AI Code Review\n\n");
        sb.append("**Quality Score:** %d/10  \n".formatted(result.qualityScore()));
        sb.append("**Verdict:** %s  \n\n".formatted(result.verdict().getDescription()));
        sb.append("### Summary\n\n").append(result.summary()).append("\n\n");

        if (!result.criticalAndMajorComments().isEmpty()) {
            sb.append("### Critical & Major Issues\n\n");
            result.criticalAndMajorComments().forEach(c ->
                    sb.append("- **%s** (`%s`): %s\n".formatted(c.filename(), c.category(), c.message()))
            );
        }

        var byCategory = result.commentsByCategory();
        if (!byCategory.isEmpty()) {
            sb.append("\n### Issue Breakdown\n\n");
            sb.append("| Category | Count |\n|---|---|\n");
            byCategory.forEach((cat, count) ->
                    sb.append("| %s | %d |\n".formatted(cat.name(), count))
            );
        }

        sb.append("\n---\n*Reviewed by AI PR Review Agent*");
        return sb.toString();
    }

    private String buildCheckRunDetails(ReviewResult result) {
        if (result.comments().isEmpty()) return "No issues found.";
        var sb = new StringBuilder("## Detailed Findings\n\n");
        result.comments().forEach(c ->
                sb.append("### %s — `%s` (line %d)\n%s\n\n"
                        .formatted(c.severity().label(), c.filename(), c.lineNumber(), c.message()))
        );
        return sb.toString();
    }
}

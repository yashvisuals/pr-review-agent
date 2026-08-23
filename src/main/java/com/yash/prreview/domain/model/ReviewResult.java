package com.yash.prreview.domain.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Immutable domain record representing the complete AI review result for a PR.
 */
public record ReviewResult(
        String reviewId,
        long pullRequestId,
        int pullRequestNumber,
        String repositoryFullName,
        List<ReviewComment> comments,
        String summary,
        int qualityScore,
        ReviewVerdict verdict,
        Instant reviewedAt,
        long analysisTimeMs
) {
    public enum ReviewVerdict {
        APPROVE("Approved — no critical issues found"),
        REQUEST_CHANGES("Changes requested — critical or major issues detected"),
        COMMENT("Commented — minor suggestions only");

        private final String description;
        ReviewVerdict(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    public List<ReviewComment> criticalAndMajorComments() {
        return comments.stream()
                .filter(c -> c.severity() instanceof ReviewSeverity.Critical
                        || c.severity() instanceof ReviewSeverity.Major)
                .sorted(Comparator.comparingInt(c -> c.severity().priority()))
                .toList();
    }

    public Map<ReviewCategory, Long> commentsByCategory() {
        return comments.stream()
                .collect(Collectors.groupingBy(ReviewComment::category, Collectors.counting()));
    }

    public boolean hasBlockingIssues() {
        return comments.stream()
                .anyMatch(c -> c.severity() instanceof ReviewSeverity.Critical);
    }

    public static ReviewVerdict deriveVerdict(List<ReviewComment> comments) {
        boolean hasCritical = comments.stream()
                .anyMatch(c -> c.severity() instanceof ReviewSeverity.Critical);
        boolean hasMajor = comments.stream()
                .anyMatch(c -> c.severity() instanceof ReviewSeverity.Major);

        if (hasCritical || hasMajor) return ReviewVerdict.REQUEST_CHANGES;
        if (comments.isEmpty()) return ReviewVerdict.APPROVE;
        return ReviewVerdict.COMMENT;
    }
}

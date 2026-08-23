package com.yash.prreview.presentation.api.dto;

import com.yash.prreview.domain.model.ReviewResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ReviewResponse(
        String reviewId,
        int pullRequestNumber,
        String repositoryFullName,
        String summary,
        int qualityScore,
        String verdict,
        String verdictDescription,
        int totalComments,
        int criticalCount,
        int majorCount,
        int minorCount,
        int suggestionCount,
        Map<String, Long> commentsByCategory,
        List<CommentResponse> topIssues,
        Instant reviewedAt,
        long analysisTimeMs
) {
    public record CommentResponse(
            String filename,
            int lineNumber,
            String severity,
            String category,
            String message,
            String suggestion
    ) {}

    public static ReviewResponse from(ReviewResult result) {
        long critical = result.comments().stream().filter(c -> "CRITICAL".equals(c.severity().label())).count();
        long major = result.comments().stream().filter(c -> "MAJOR".equals(c.severity().label())).count();
        long minor = result.comments().stream().filter(c -> "MINOR".equals(c.severity().label())).count();
        long suggestion = result.comments().stream().filter(c -> "SUGGESTION".equals(c.severity().label())).count();

        Map<String, Long> byCategory = result.comments().stream()
                .collect(Collectors.groupingBy(c -> c.category().name(), Collectors.counting()));

        List<CommentResponse> topIssues = result.criticalAndMajorComments().stream()
                .limit(10)
                .map(c -> new CommentResponse(
                        c.filename(), c.lineNumber(), c.severity().label(),
                        c.category().name(), c.message(), c.suggestion()))
                .toList();

        return new ReviewResponse(
                result.reviewId(),
                result.pullRequestNumber(),
                result.repositoryFullName(),
                result.summary(),
                result.qualityScore(),
                result.verdict().name(),
                result.verdict().getDescription(),
                result.comments().size(),
                (int) critical, (int) major, (int) minor, (int) suggestion,
                byCategory, topIssues,
                result.reviewedAt(), result.analysisTimeMs()
        );
    }
}

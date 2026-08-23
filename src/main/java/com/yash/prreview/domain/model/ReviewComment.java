package com.yash.prreview.domain.model;

/**
 * Immutable domain record representing a single review comment on a specific file/line.
 */
public record ReviewComment(
        String filename,
        int lineNumber,
        ReviewSeverity severity,
        ReviewCategory category,
        String message,
        String suggestion,
        String codeSnippet
) {
    public ReviewComment {
        if (filename == null || filename.isBlank()) throw new IllegalArgumentException("filename required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message required");
    }

    public String toMarkdown() {
        return """
                **%s** [%s]

                %s

                %s
                """.formatted(
                severity.formatForGitHub(),
                category.name(),
                message,
                suggestion != null ? "**Suggestion:** " + suggestion : ""
        );
    }
}

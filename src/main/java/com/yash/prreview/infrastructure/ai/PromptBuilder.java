package com.yash.prreview.infrastructure.ai;

import com.yash.prreview.domain.model.PullRequest;
import com.yash.prreview.domain.model.ReviewCategory;
import com.yash.prreview.domain.model.ReviewComment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds structured prompts for the AI model.
 * Uses Java 15+ text blocks for readable, maintainable prompt templates.
 */
@Component
public class PromptBuilder {

    public String buildFileAnalysisPrompt(PullRequest.ChangedFile file, ReviewCategory category) {
        String focusInstructions = switch (category) {
            case SECURITY -> """
                    Focus ONLY on security issues:
                    - SQL injection, XSS, CSRF vulnerabilities
                    - Hardcoded secrets or credentials
                    - Insecure deserialization
                    - Missing authentication/authorization checks
                    - Sensitive data exposure
                    - Dependency vulnerabilities
                    """;
            case BUGS -> """
                    Focus ONLY on potential bugs:
                    - Null pointer dereferences
                    - Resource leaks (unclosed streams/connections)
                    - Race conditions and concurrency issues
                    - Off-by-one errors
                    - Incorrect error handling (swallowed exceptions)
                    - Logic errors and incorrect conditions
                    """;
            case PERFORMANCE -> """
                    Focus ONLY on performance issues:
                    - N+1 database query patterns
                    - Missing database indexes (in SQL files)
                    - Unnecessary object creation in loops
                    - Blocking calls in async/reactive contexts
                    - Missing pagination for large data sets
                    - Inefficient algorithms (O(n²) where O(n log n) works)
                    """;
            case CODE_QUALITY -> """
                    Focus ONLY on code quality:
                    - Violation of SOLID principles
                    - God classes or methods doing too much
                    - Magic numbers or strings (use constants)
                    - Duplicated code (DRY principle violations)
                    - Poor naming conventions
                    - Missing or inadequate documentation for public APIs
                    """;
            default -> "Review for general code issues.";
        };

        return """
                You are an expert %s code reviewer. Analyze this git diff and find issues.

                File: %s
                Language: %s

                %s

                Git diff:
                ```
                %s
                ```

                Return a JSON array of issues found. Each issue must have:
                - "line": the line number in the diff where the issue occurs (integer, 0 if file-level)
                - "severity": one of "CRITICAL", "MAJOR", "MINOR", "SUGGESTION"
                - "message": clear description of the problem (max 200 chars)
                - "suggestion": how to fix it (max 200 chars)

                Return ONLY a valid JSON array, no explanation text. Example:
                [{"line": 42, "severity": "MAJOR", "message": "Potential NPE here", "suggestion": "Add null check before calling method"}]

                If no issues found, return an empty array: []
                """.formatted(
                file.language(),
                file.filename(),
                file.language(),
                focusInstructions,
                truncatePatch(file.patch())
        );
    }

    public String buildSummaryPrompt(PullRequest pr, List<ReviewComment> comments) {
        return """
                You are a technical lead reviewing a pull request. Provide a concise summary.

                PR: %s
                Repository: %s
                Author: %s
                Files changed: %d
                Lines added: +%d, Lines removed: -%d

                Issues found by category:
                %s

                PR Description:
                %s

                Write a 2-3 sentence professional summary covering:
                1. What the PR does
                2. The most important issues found
                3. Overall assessment

                Keep it under 300 characters. Return only the summary text, no JSON.
                """.formatted(
                pr.title(),
                pr.repositoryFullName(),
                pr.author(),
                pr.changedFiles().size(),
                pr.totalAdditions(),
                pr.totalDeletions(),
                buildIssueSummary(comments),
                pr.body() != null ? pr.body().substring(0, Math.min(500, pr.body().length())) : "No description"
        );
    }

    public String buildScorePrompt(PullRequest pr, List<ReviewComment> comments) {
        long critical = comments.stream()
                .filter(c -> c.severity().label().equals("CRITICAL")).count();
        long major = comments.stream()
                .filter(c -> c.severity().label().equals("MAJOR")).count();
        long minor = comments.stream()
                .filter(c -> c.severity().label().equals("MINOR")).count();

        return """
                Rate this PR on a scale of 1-10 where:
                10 = Perfect, no issues
                7-9 = Good with minor suggestions
                4-6 = Needs improvement
                1-3 = Major issues requiring significant rework

                Stats:
                - Critical issues: %d
                - Major issues: %d
                - Minor issues: %d
                - Files changed: %d

                Return ONLY a single integer from 1 to 10. No explanation.
                """.formatted(critical, major, minor, pr.changedFiles().size());
    }

    private String buildIssueSummary(List<ReviewComment> comments) {
        if (comments.isEmpty()) return "- None";
        return comments.stream()
                .limit(5)
                .map(c -> "- [%s] %s: %s".formatted(c.severity().label(), c.category(), c.message()))
                .reduce("", (a, b) -> a + "\n" + b);
    }

    private String truncatePatch(String patch) {
        if (patch == null) return "";
        int maxLen = 3000;
        return patch.length() > maxLen ? patch.substring(0, maxLen) + "\n... (truncated)" : patch;
    }
}

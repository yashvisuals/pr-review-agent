package com.yash.prreview.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Domain record representing a GitHub Pull Request.
 */
public record PullRequest(
        long id,
        int number,
        String title,
        String body,
        String repositoryFullName,
        String author,
        String headBranch,
        String baseBranch,
        String headSha,
        List<ChangedFile> changedFiles,
        Instant createdAt,
        PrState state
) {
    public enum PrState { OPEN, CLOSED, MERGED }

    public record ChangedFile(
            String filename,
            String status,
            int additions,
            int deletions,
            String patch
    ) {
        public boolean isDeleted() { return "removed".equals(status); }
        public boolean isAdded() { return "added".equals(status); }
        public int totalChanges() { return additions + deletions; }

        public String language() {
            if (filename.endsWith(".java")) return "java";
            if (filename.endsWith(".py")) return "python";
            if (filename.endsWith(".ts") || filename.endsWith(".js")) return "typescript";
            if (filename.endsWith(".go")) return "go";
            if (filename.endsWith(".sql")) return "sql";
            return "unknown";
        }
    }

    public int totalAdditions() {
        return changedFiles.stream().mapToInt(ChangedFile::additions).sum();
    }

    public int totalDeletions() {
        return changedFiles.stream().mapToInt(ChangedFile::deletions).sum();
    }

    public boolean isLarge() {
        return totalAdditions() + totalDeletions() > 500;
    }
}

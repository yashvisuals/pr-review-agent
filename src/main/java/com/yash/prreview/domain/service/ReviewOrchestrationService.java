package com.yash.prreview.domain.service;

import com.yash.prreview.domain.model.*;
import com.yash.prreview.domain.port.AiReviewPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Core domain service orchestrating parallel AI analysis using Java 21+ Virtual Threads.
 *
 * Key design: each file × each analysis dimension runs as a separate virtual thread,
 * allowing massive concurrency without the overhead of platform threads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewOrchestrationService {

    private final AiReviewPort aiReviewPort;

    private static final List<ReviewCategory> ANALYSIS_DIMENSIONS = List.of(
            ReviewCategory.SECURITY,
            ReviewCategory.BUGS,
            ReviewCategory.PERFORMANCE,
            ReviewCategory.CODE_QUALITY
    );

    /**
     * Orchestrates parallel AI analysis of a PR using virtual threads.
     * Each file is analyzed across all dimensions concurrently.
     *
     * Java 21+ Virtual Threads make this highly efficient:
     * - No thread pool exhaustion for I/O-bound AI calls
     * - True concurrency per file × dimension combination
     */
    public ReviewResult reviewPullRequest(PullRequest pullRequest) {
        long startTime = System.currentTimeMillis();
        log.info("Starting parallel review for PR #{} in {}", pullRequest.number(), pullRequest.repositoryFullName());

        // Virtual thread executor — each task gets its own lightweight virtual thread
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<ReviewComment> allComments = analyzeAllFilesInParallel(pullRequest, executor);
            String summary = aiReviewPort.generatePrSummary(pullRequest, allComments);
            int score = aiReviewPort.calculateQualityScore(pullRequest, allComments);
            ReviewResult.ReviewVerdict verdict = ReviewResult.deriveVerdict(allComments);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Review completed for PR #{}: {} comments, score={}, verdict={}, took={}ms",
                    pullRequest.number(), allComments.size(), score, verdict, elapsed);

            return new ReviewResult(
                    UUID.randomUUID().toString(),
                    pullRequest.id(),
                    pullRequest.number(),
                    pullRequest.repositoryFullName(),
                    allComments,
                    summary,
                    score,
                    verdict,
                    Instant.now(),
                    elapsed
            );
        }
    }

    private List<ReviewComment> analyzeAllFilesInParallel(PullRequest pr, Executor executor) {
        // Build a future for every (file, dimension) pair
        List<CompletableFuture<List<ReviewComment>>> futures = pr.changedFiles().stream()
                .filter(f -> !f.isDeleted() && f.patch() != null && !f.patch().isBlank())
                .flatMap(file -> ANALYSIS_DIMENSIONS.stream()
                        .map(dimension -> CompletableFuture.supplyAsync(
                                () -> {
                                    log.debug("Analyzing {} for {} in {}", file.filename(), dimension, pr.repositoryFullName());
                                    return aiReviewPort.analyzeFile(file, dimension);
                                },
                                executor
                        ).exceptionally(ex -> {
                            log.warn("Analysis failed for {} / {}: {}", file.filename(), dimension, ex.getMessage());
                            return List.of();
                        })
                ))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Collection::stream)
                .sorted(Comparator.comparingInt(c -> c.severity().priority()))
                .collect(Collectors.toList());
    }
}

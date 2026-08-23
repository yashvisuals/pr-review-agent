package com.yash.prreview.presentation.api;

import com.yash.prreview.application.usecase.GetReviewHistoryUseCase;
import com.yash.prreview.application.usecase.TriggerManualReviewUseCase;
import com.yash.prreview.domain.model.ReviewResult;
import com.yash.prreview.presentation.api.dto.ManualReviewRequest;
import com.yash.prreview.presentation.api.dto.ReviewResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for review history and manual triggering.
 * Follows RESTful conventions with proper HTTP status codes.
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final GetReviewHistoryUseCase getReviewHistoryUseCase;
    private final TriggerManualReviewUseCase triggerManualReviewUseCase;

    public ReviewController(GetReviewHistoryUseCase getReviewHistoryUseCase,
                             TriggerManualReviewUseCase triggerManualReviewUseCase) {
        this.getReviewHistoryUseCase = getReviewHistoryUseCase;
        this.triggerManualReviewUseCase = triggerManualReviewUseCase;
    }

    /**
     * GET /api/v1/reviews/{reviewId}
     * Retrieve a specific review by ID.
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> getReview(@PathVariable String reviewId) {
        return getReviewHistoryUseCase.getById(reviewId)
                .map(ReviewResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/reviews?repository=owner/repo&page=0&size=20
     * List review history for a repository.
     */
    @GetMapping
    public ResponseEntity<List<ReviewResponse>> getReviewHistory(
            @RequestParam String repository,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<ReviewResponse> reviews = getReviewHistoryUseCase
                .getByRepository(repository, page, Math.min(size, 100))
                .stream()
                .map(ReviewResponse::from)
                .toList();

        return ResponseEntity.ok(reviews);
    }

    /**
     * POST /api/v1/reviews/trigger
     * Manually trigger a review for any PR without a webhook.
     * Useful for CI pipeline integration.
     */
    @PostMapping("/trigger")
    public ResponseEntity<ReviewResponse> triggerReview(@Valid @RequestBody ManualReviewRequest request) {
        log.info("Manual review requested for {}/{} PR #{}", request.owner(), request.repo(), request.prNumber());

        ReviewResult result = triggerManualReviewUseCase.execute(
                request.owner(), request.repo(), request.prNumber());

        return ResponseEntity.ok(ReviewResponse.from(result));
    }
}

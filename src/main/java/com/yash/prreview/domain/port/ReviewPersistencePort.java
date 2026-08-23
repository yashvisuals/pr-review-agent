package com.yash.prreview.domain.port;

import com.yash.prreview.domain.model.ReviewResult;

import java.util.List;
import java.util.Optional;

/**
 * Output port for review persistence.
 */
public interface ReviewPersistencePort {

    ReviewResult save(ReviewResult result);

    Optional<ReviewResult> findByPullRequestId(long pullRequestId);

    List<ReviewResult> findByRepository(String repositoryFullName, int page, int size);

    Optional<ReviewResult> findById(String reviewId);

    void deleteById(String reviewId);
}

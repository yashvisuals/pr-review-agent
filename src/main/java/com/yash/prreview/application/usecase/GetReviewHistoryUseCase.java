package com.yash.prreview.application.usecase;

import com.yash.prreview.domain.model.ReviewResult;
import com.yash.prreview.domain.port.ReviewPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetReviewHistoryUseCase {

    private final ReviewPersistencePort persistencePort;

    @Cacheable(value = "reviews", key = "#reviewId")
    public Optional<ReviewResult> getById(String reviewId) {
        return persistencePort.findById(reviewId);
    }

    @Cacheable(value = "review-history", key = "#repository + '-' + #page + '-' + #size")
    public List<ReviewResult> getByRepository(String repository, int page, int size) {
        return persistencePort.findByRepository(repository, page, size);
    }
}

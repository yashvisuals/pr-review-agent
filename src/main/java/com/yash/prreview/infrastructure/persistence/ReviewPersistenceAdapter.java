package com.yash.prreview.infrastructure.persistence;

import com.yash.prreview.domain.model.*;
import com.yash.prreview.domain.port.ReviewPersistencePort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of ReviewPersistencePort.
 * Maps between domain records and JPA entities.
 */
@Component
public class ReviewPersistenceAdapter implements ReviewPersistencePort {

    private final ReviewJpaRepository jpaRepository;

    public ReviewPersistenceAdapter(ReviewJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public ReviewResult save(ReviewResult result) {
        ReviewEntity entity = toEntity(result);
        jpaRepository.save(entity);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewResult> findByPullRequestId(long pullRequestId) {
        return jpaRepository.findByPullRequestId(pullRequestId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResult> findByRepository(String repositoryFullName, int page, int size) {
        return jpaRepository.findByRepositoryFullNameOrderByReviewedAtDesc(
                repositoryFullName, PageRequest.of(page, size))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewResult> findById(String reviewId) {
        return jpaRepository.findById(reviewId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(String reviewId) {
        jpaRepository.deleteById(reviewId);
    }

    private ReviewEntity toEntity(ReviewResult result) {
        ReviewEntity entity = new ReviewEntity();
        entity.setReviewId(result.reviewId());
        entity.setPullRequestId(result.pullRequestId());
        entity.setPullRequestNumber(result.pullRequestNumber());
        entity.setRepositoryFullName(result.repositoryFullName());
        entity.setSummary(result.summary());
        entity.setQualityScore(result.qualityScore());
        entity.setVerdict(result.verdict());
        entity.setReviewedAt(result.reviewedAt());
        entity.setAnalysisTimeMs(result.analysisTimeMs());

        List<ReviewCommentEntity> commentEntities = result.comments().stream()
                .map(c -> {
                    ReviewCommentEntity ce = new ReviewCommentEntity();
                    ce.setReview(entity);
                    ce.setFilename(c.filename());
                    ce.setLineNumber(c.lineNumber());
                    ce.setSeverity(c.severity().label());
                    ce.setCategory(c.category().name());
                    ce.setMessage(c.message());
                    ce.setSuggestion(c.suggestion());
                    return ce;
                })
                .toList();

        entity.setComments(commentEntities);
        return entity;
    }

    private ReviewResult toDomain(ReviewEntity entity) {
        List<ReviewComment> comments = entity.getComments().stream()
                .map(ce -> new ReviewComment(
                        ce.getFilename(),
                        ce.getLineNumber() != null ? ce.getLineNumber() : 0,
                        ReviewSeverity.fromString(ce.getSeverity()),
                        ReviewCategory.valueOf(ce.getCategory()),
                        ce.getMessage(),
                        ce.getSuggestion(),
                        null
                ))
                .toList();

        return new ReviewResult(
                entity.getReviewId(),
                entity.getPullRequestId(),
                entity.getPullRequestNumber(),
                entity.getRepositoryFullName(),
                comments,
                entity.getSummary(),
                entity.getQualityScore(),
                entity.getVerdict(),
                entity.getReviewedAt(),
                entity.getAnalysisTimeMs() != null ? entity.getAnalysisTimeMs() : 0L
        );
    }
}

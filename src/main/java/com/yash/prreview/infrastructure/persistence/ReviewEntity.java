package com.yash.prreview.infrastructure.persistence;

import com.yash.prreview.domain.model.ReviewResult;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_pr_id", columnList = "pull_request_id"),
        @Index(name = "idx_reviews_repo", columnList = "repository_full_name"),
        @Index(name = "idx_reviews_reviewed_at", columnList = "reviewed_at")
})
public class ReviewEntity {

    @Id
    @Column(name = "review_id", length = 36)
    private String reviewId;

    @Column(name = "pull_request_id", nullable = false)
    private Long pullRequestId;

    @Column(name = "pull_request_number", nullable = false)
    private Integer pullRequestNumber;

    @Column(name = "repository_full_name", nullable = false, length = 200)
    private String repositoryFullName;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "quality_score")
    private Integer qualityScore;

    @Column(name = "verdict", length = 50)
    @Enumerated(EnumType.STRING)
    private ReviewResult.ReviewVerdict verdict;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "analysis_time_ms")
    private Long analysisTimeMs;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReviewCommentEntity> comments = new ArrayList<>();

    public ReviewEntity() {}

    // Getters and setters
    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }
    public Long getPullRequestId() { return pullRequestId; }
    public void setPullRequestId(Long pullRequestId) { this.pullRequestId = pullRequestId; }
    public Integer getPullRequestNumber() { return pullRequestNumber; }
    public void setPullRequestNumber(Integer pullRequestNumber) { this.pullRequestNumber = pullRequestNumber; }
    public String getRepositoryFullName() { return repositoryFullName; }
    public void setRepositoryFullName(String repositoryFullName) { this.repositoryFullName = repositoryFullName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
    public ReviewResult.ReviewVerdict getVerdict() { return verdict; }
    public void setVerdict(ReviewResult.ReviewVerdict verdict) { this.verdict = verdict; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Long getAnalysisTimeMs() { return analysisTimeMs; }
    public void setAnalysisTimeMs(Long analysisTimeMs) { this.analysisTimeMs = analysisTimeMs; }
    public List<ReviewCommentEntity> getComments() { return comments; }
    public void setComments(List<ReviewCommentEntity> comments) { this.comments = comments; }
}

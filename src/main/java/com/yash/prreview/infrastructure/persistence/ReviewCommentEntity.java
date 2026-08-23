package com.yash.prreview.infrastructure.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "review_comments", indexes = {
        @Index(name = "idx_comments_review_id", columnList = "review_id"),
        @Index(name = "idx_comments_filename", columnList = "filename")
})
public class ReviewCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private ReviewEntity review;

    @Column(name = "filename", nullable = false, length = 500)
    private String filename;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    public ReviewCommentEntity() {}

    public Long getId() { return id; }
    public ReviewEntity getReview() { return review; }
    public void setReview(ReviewEntity review) { this.review = review; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
}

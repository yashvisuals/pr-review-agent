-- Flyway migration V1: Create review tables
-- Using PostgreSQL-specific features for better performance

CREATE TABLE reviews (
    review_id          VARCHAR(36)     PRIMARY KEY,
    pull_request_id    BIGINT          NOT NULL,
    pull_request_number INTEGER        NOT NULL,
    repository_full_name VARCHAR(200)  NOT NULL,
    summary            TEXT,
    quality_score      INTEGER         CHECK (quality_score BETWEEN 1 AND 10),
    verdict            VARCHAR(50)     NOT NULL,
    reviewed_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    analysis_time_ms   BIGINT
);

CREATE INDEX idx_reviews_pr_id ON reviews (pull_request_id);
CREATE INDEX idx_reviews_repo ON reviews (repository_full_name);
CREATE INDEX idx_reviews_reviewed_at ON reviews (reviewed_at DESC);
CREATE INDEX idx_reviews_verdict ON reviews (verdict);

CREATE TABLE review_comments (
    id          BIGSERIAL       PRIMARY KEY,
    review_id   VARCHAR(36)     NOT NULL REFERENCES reviews(review_id) ON DELETE CASCADE,
    filename    VARCHAR(500)    NOT NULL,
    line_number INTEGER,
    severity    VARCHAR(20)     NOT NULL,
    category    VARCHAR(50)     NOT NULL,
    message     TEXT            NOT NULL,
    suggestion  TEXT
);

CREATE INDEX idx_comments_review_id ON review_comments (review_id);
CREATE INDEX idx_comments_severity ON review_comments (severity);
CREATE INDEX idx_comments_filename ON review_comments (filename);

COMMENT ON TABLE reviews IS 'AI code review results for GitHub pull requests';
COMMENT ON TABLE review_comments IS 'Individual review findings per file and line';

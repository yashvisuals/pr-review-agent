package com.yash.prreview.domain.service;

import com.yash.prreview.domain.model.*;
import com.yash.prreview.domain.port.AiReviewPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewOrchestrationService - parallel AI analysis")
class ReviewOrchestrationServiceTest {

    @Mock
    private AiReviewPort aiReviewPort;

    private ReviewOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new ReviewOrchestrationService(aiReviewPort);
    }

    @Test
    @DisplayName("should aggregate comments from all analysis dimensions in parallel")
    void shouldAggregateCommentsFromAllDimensions() {
        PullRequest pr = buildTestPr();

        when(aiReviewPort.analyzeFile(any(), any())).thenAnswer(invocation -> {
            ReviewCategory category = invocation.getArgument(1);
            return List.of(new ReviewComment(
                    "src/Test.java", 10,
                    new ReviewSeverity.Minor("test"),
                    category,
                    "Test finding for " + category,
                    "Fix suggestion",
                    null
            ));
        });
        when(aiReviewPort.generatePrSummary(any(), any())).thenReturn("Test summary");
        when(aiReviewPort.calculateQualityScore(any(), any())).thenReturn(8);

        ReviewResult result = service.reviewPullRequest(pr);

        // 1 file × 4 dimensions = 4 comments
        assertThat(result.comments()).hasSize(4);
        assertThat(result.qualityScore()).isEqualTo(8);
        assertThat(result.summary()).isEqualTo("Test summary");
        assertThat(result.reviewId()).isNotBlank();
        assertThat(result.analysisTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("should derive APPROVE verdict when no critical/major issues")
    void shouldDeriveApproveVerdict() {
        PullRequest pr = buildTestPr();

        when(aiReviewPort.analyzeFile(any(), any())).thenReturn(List.of());
        when(aiReviewPort.generatePrSummary(any(), any())).thenReturn("LGTM");
        when(aiReviewPort.calculateQualityScore(any(), any())).thenReturn(9);

        ReviewResult result = service.reviewPullRequest(pr);

        assertThat(result.verdict()).isEqualTo(ReviewResult.ReviewVerdict.APPROVE);
        assertThat(result.hasBlockingIssues()).isFalse();
    }

    @Test
    @DisplayName("should derive REQUEST_CHANGES verdict when critical issues exist")
    void shouldDeriveRequestChangesVerdictForCriticalIssues() {
        PullRequest pr = buildTestPr();

        when(aiReviewPort.analyzeFile(any(), any())).thenAnswer(invocation -> {
            ReviewCategory category = invocation.getArgument(1);
            if (category == ReviewCategory.SECURITY) {
                return List.of(new ReviewComment(
                        "src/Test.java", 5,
                        new ReviewSeverity.Critical("SQL injection"),
                        ReviewCategory.SECURITY,
                        "SQL injection vulnerability",
                        "Use prepared statements",
                        null
                ));
            }
            return List.of();
        });
        when(aiReviewPort.generatePrSummary(any(), any())).thenReturn("Critical issues found");
        when(aiReviewPort.calculateQualityScore(any(), any())).thenReturn(3);

        ReviewResult result = service.reviewPullRequest(pr);

        assertThat(result.verdict()).isEqualTo(ReviewResult.ReviewVerdict.REQUEST_CHANGES);
        assertThat(result.hasBlockingIssues()).isTrue();
    }

    @Test
    @DisplayName("should skip deleted files during analysis")
    void shouldSkipDeletedFiles() {
        PullRequest prWithDeletedFile = new PullRequest(
                1L, 42, "Test PR", "Description",
                "owner/repo", "testuser", "feature", "main", "abc123",
                List.of(
                        new PullRequest.ChangedFile("deleted.java", "removed", 0, 100, null),
                        new PullRequest.ChangedFile("active.java", "modified", 10, 5, "@@ -1 +1 @@ some change")
                ),
                Instant.now(), PullRequest.PrState.OPEN
        );

        when(aiReviewPort.analyzeFile(any(), any())).thenReturn(List.of());
        when(aiReviewPort.generatePrSummary(any(), any())).thenReturn("OK");
        when(aiReviewPort.calculateQualityScore(any(), any())).thenReturn(10);

        ReviewResult result = service.reviewPullRequest(prWithDeletedFile);

        assertThat(result).isNotNull();
    }

    private PullRequest buildTestPr() {
        return new PullRequest(
                1L, 42, "Add new feature", "Description",
                "owner/repo", "testuser", "feature", "main", "abc123",
                List.of(new PullRequest.ChangedFile(
                        "src/main/java/Test.java", "modified", 30, 5,
                        "@@ -10,5 +10,10 @@\n+    public void newMethod() {\n+    }"
                )),
                Instant.now(), PullRequest.PrState.OPEN
        );
    }
}

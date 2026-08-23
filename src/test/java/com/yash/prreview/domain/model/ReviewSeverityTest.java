package com.yash.prreview.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewSeverityTest {

    @Test
    void criticalHasHighestPriority() {
        assertThat(new ReviewSeverity.Critical("r").priority()).isLessThan(new ReviewSeverity.Major("r").priority());
        assertThat(new ReviewSeverity.Major("r").priority()).isLessThan(new ReviewSeverity.Minor("r").priority());
        assertThat(new ReviewSeverity.Minor("r").priority()).isLessThan(new ReviewSeverity.Suggestion("r").priority());
    }

    @ParameterizedTest
    @MethodSource("severityFormats")
    void formatForGitHubContainsSeverityLabel(ReviewSeverity severity, String expectedPrefix) {
        assertThat(severity.formatForGitHub()).startsWith(expectedPrefix);
    }

    static Stream<Arguments> severityFormats() {
        return Stream.of(
                Arguments.of(new ReviewSeverity.Critical("r"), "🚨"),
                Arguments.of(new ReviewSeverity.Major("r"), "⚠️"),
                Arguments.of(new ReviewSeverity.Minor("r"), "💡"),
                Arguments.of(new ReviewSeverity.Suggestion("r"), "💭")
        );
    }

    @Test
    void patternMatchingExhaustiveness() {
        // Verify sealed interface pattern matching works for all types
        ReviewSeverity[] severities = {
                new ReviewSeverity.Critical("c"),
                new ReviewSeverity.Major("m"),
                new ReviewSeverity.Minor("mi"),
                new ReviewSeverity.Suggestion("s")
        };

        for (ReviewSeverity severity : severities) {
            String result = switch (severity) {
                case ReviewSeverity.Critical c -> "critical";
                case ReviewSeverity.Major m -> "major";
                case ReviewSeverity.Minor m -> "minor";
                case ReviewSeverity.Suggestion s -> "suggestion";
            };
            assertThat(result).isNotBlank();
        }
    }
}

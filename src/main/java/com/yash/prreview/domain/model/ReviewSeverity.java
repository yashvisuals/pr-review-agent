package com.yash.prreview.domain.model;

/**
 * Sealed interface hierarchy representing review finding severity levels.
 * Uses Java 17+ sealed interfaces with pattern matching for exhaustive handling.
 */
public sealed interface ReviewSeverity
        permits ReviewSeverity.Critical, ReviewSeverity.Major, ReviewSeverity.Minor, ReviewSeverity.Suggestion {

    String label();
    int priority();

    record Critical(String reason) implements ReviewSeverity {
        public String label() { return "CRITICAL"; }
        public int priority() { return 1; }
    }

    record Major(String reason) implements ReviewSeverity {
        public String label() { return "MAJOR"; }
        public int priority() { return 2; }
    }

    record Minor(String reason) implements ReviewSeverity {
        public String label() { return "MINOR"; }
        public int priority() { return 3; }
    }

    record Suggestion(String description) implements ReviewSeverity {
        public String label() { return "SUGGESTION"; }
        public int priority() { return 4; }
    }

    static ReviewSeverity fromString(String value) {
        return switch (value.toUpperCase()) {
            case "CRITICAL" -> new Critical("AI detected");
            case "MAJOR" -> new Major("AI detected");
            case "MINOR" -> new Minor("AI detected");
            default -> new Suggestion("AI detected");
        };
    }

    default String formatForGitHub() {
        return switch (this) {
            case Critical c -> "🚨 **CRITICAL**: " + c.reason();
            case Major m -> "⚠️ **MAJOR**: " + m.reason();
            case Minor min -> "💡 **MINOR**: " + min.reason();
            case Suggestion s -> "💭 **SUGGESTION**: " + s.description();
        };
    }
}

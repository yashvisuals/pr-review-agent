package com.yash.prreview.domain.model;

public enum ReviewCategory {
    SECURITY("Security vulnerability or risk"),
    BUGS("Potential bug or logical error"),
    PERFORMANCE("Performance issue or inefficiency"),
    CODE_QUALITY("Code quality, readability, or maintainability"),
    TESTING("Missing or inadequate tests"),
    DESIGN("Architectural or design concern");

    private final String description;

    ReviewCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

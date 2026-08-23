package com.yash.prreview.presentation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ManualReviewRequest(
        @NotBlank(message = "owner is required")
        String owner,

        @NotBlank(message = "repo is required")
        String repo,

        @Min(value = 1, message = "prNumber must be positive")
        int prNumber
) {}

package com.yash.prreview.infrastructure.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPayload(
        String action,
        @JsonProperty("pull_request") PullRequestPayload pullRequest,
        RepositoryPayload repository,
        SenderPayload sender,
        InstallationPayload installation
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestPayload(
            long id,
            int number,
            String title,
            String body,
            String state,
            UserPayload user,
            HeadPayload head,
            BasePayload base,
            @JsonProperty("created_at") String createdAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeadPayload(String ref, String sha) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BasePayload(String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserPayload(String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryPayload(
            long id,
            String name,
            @JsonProperty("full_name") String fullName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SenderPayload(String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstallationPayload(long id) {}
}

package com.yash.prreview.infrastructure.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubFileDto(
        String filename,
        String status,
        int additions,
        int deletions,
        int changes,
        String patch,
        @JsonProperty("blob_url") String blobUrl,
        @JsonProperty("raw_url") String rawUrl
) {}

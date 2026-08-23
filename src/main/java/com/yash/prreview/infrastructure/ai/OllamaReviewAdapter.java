package com.yash.prreview.infrastructure.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yash.prreview.domain.model.*;
import com.yash.prreview.domain.port.AiReviewPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI review adapter using Spring AI with Ollama (free, local LLM).
 *
 * Spring AI abstraction means swapping to Gemini/GPT/Claude requires only
 * changing the starter dependency and config — zero code changes here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaReviewAdapter implements AiReviewPort {

    private final ChatClient chatClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Override
    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 500))
    public List<ReviewComment> analyzeFile(PullRequest.ChangedFile file, ReviewCategory category) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            String prompt = promptBuilder.buildFileAnalysisPrompt(file, category);

            String rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            List<ReviewComment> comments = parseAiResponse(rawResponse, file, category);

            sample.stop(meterRegistry.timer("ai.analysis.duration",
                    "category", category.name(),
                    "language", file.language()));

            meterRegistry.counter("ai.analysis.completed",
                    "category", category.name()).increment();

            return comments;

        } catch (Exception e) {
            log.warn("AI analysis failed for {} / {}: {}", file.filename(), category, e.getMessage());
            meterRegistry.counter("ai.analysis.failed", "category", category.name()).increment();
            return List.of();
        }
    }

    @Override
    public String generatePrSummary(PullRequest pullRequest, List<ReviewComment> allComments) {
        try {
            String prompt = promptBuilder.buildSummaryPrompt(pullRequest, allComments);
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .trim();
        } catch (Exception e) {
            log.warn("Failed to generate PR summary: {}", e.getMessage());
            return "AI review completed. %d issues found across %d files."
                    .formatted(allComments.size(), pullRequest.changedFiles().size());
        }
    }

    @Override
    public int calculateQualityScore(PullRequest pullRequest, List<ReviewComment> comments) {
        try {
            String prompt = promptBuilder.buildScorePrompt(pullRequest, comments);
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .trim();

            // Extract first integer from response
            return Integer.parseInt(response.replaceAll("[^0-9]", "").substring(0, 1));
        } catch (Exception e) {
            log.warn("Failed to calculate quality score, using heuristic: {}", e.getMessage());
            return calculateHeuristicScore(comments);
        }
    }

    private List<ReviewComment> parseAiResponse(String rawResponse, PullRequest.ChangedFile file, ReviewCategory category) {
        try {
            // Extract JSON array from response (model may include extra text)
            String jsonPart = extractJsonArray(rawResponse);
            List<Map<String, Object>> items = objectMapper.readValue(jsonPart, new TypeReference<>() {});

            return items.stream()
                    .map(item -> {
                        int line = item.containsKey("line") ? ((Number) item.get("line")).intValue() : 0;
                        String severityStr = (String) item.getOrDefault("severity", "MINOR");
                        String message = (String) item.getOrDefault("message", "Issue detected");
                        String suggestion = (String) item.getOrDefault("suggestion", "");

                        return new ReviewComment(
                                file.filename(),
                                line,
                                ReviewSeverity.fromString(severityStr),
                                category,
                                message,
                                suggestion,
                                null
                        );
                    })
                    .toList();
        } catch (Exception e) {
            log.debug("Could not parse AI response as JSON for {}: {}", file.filename(), e.getMessage());
            return List.of();
        }
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start == -1 || end == -1 || end < start) return "[]";
        return text.substring(start, end + 1);
    }

    private int calculateHeuristicScore(List<ReviewComment> comments) {
        long critical = comments.stream().filter(c -> c.severity() instanceof ReviewSeverity.Critical).count();
        long major = comments.stream().filter(c -> c.severity() instanceof ReviewSeverity.Major).count();
        long minor = comments.stream().filter(c -> c.severity() instanceof ReviewSeverity.Minor).count();

        int score = 10;
        score -= (int) (critical * 3);
        score -= (int) (major * 1);
        score -= (int) (minor / 3);
        return Math.max(1, Math.min(10, score));
    }
}

package com.yash.prreview.infrastructure.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Direct Groq API client using WebClient.
 * Groq provides free inference on Llama 3.3 70B via an OpenAI-compatible REST API.
 * No SDK needed — clean HTTP integration.
 */
@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final WebClient webClient;
    private final String model;
    private final String systemPrompt;

    public GroqClient(
            @Value("${groq.api-key}") String apiKey,
            @Value("${groq.model:llama-3.3-70b-versatile}") String model) {
        this.model = model;
        this.systemPrompt = """
                You are an expert software engineer and code reviewer with deep knowledge of:
                - Security vulnerabilities (OWASP Top 10)
                - Java, Python, TypeScript best practices
                - Clean code principles and design patterns
                - Performance optimization
                You are precise, concise, and focus only on actionable issues.
                Always respond with valid JSON when asked for structured output.
                """;

        this.webClient = WebClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String chat(String userPrompt) {
        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 0.1,
                "max_tokens", 4096,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        Map<?, ?> response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return extractContent(response);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        if (response == null) return "";
        var choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) return "";
        var message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        return message != null ? (String) message.get("content") : "";
    }
}

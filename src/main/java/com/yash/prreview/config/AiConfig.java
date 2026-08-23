package com.yash.prreview.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Spring AI ChatClient with a system prompt.
 *
 * Uses Spring AI's ChatModel abstraction — swapping providers (Groq → Gemini → Claude)
 * requires only changing the starter in pom.xml and base-url/api-key in application.yml.
 * Zero code changes here.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        You are an expert software engineer and code reviewer with deep knowledge of:
                        - Security vulnerabilities (OWASP Top 10)
                        - Java, Python, TypeScript best practices
                        - Clean code principles and design patterns
                        - Performance optimization

                        You are precise, concise, and focus only on actionable issues.
                        Always respond with valid JSON when asked for structured output.
                        """)
                .build();
    }
}

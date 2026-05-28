package com.example.Dawson.Bungalow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Value("${pinecone.api-key}")
    private String pineconeKey;

    @Value("${pinecone.index-url}")
    private String pineconeIndexUrl;

    @Value("${groq.api-key}")
    private String groqKey;

    @Value("${groq.model}")
    private String groqModel;

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer
                    .defaultCodecs()
                    .maxInMemorySize(2 * 1024 * 1024)) // 2MB buffer
            .build();

    public String findRelevantContext(String question) {
        try {
            Map<String, Object> queryInputs = new HashMap<>();
            queryInputs.put("text", question);

            Map<String, Object> query = new HashMap<>();
            query.put("inputs", queryInputs);
            query.put("top_k", 3);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("fields", List.of("question", "answer"));

            log.info("🔍 Pinecone URL: {}", pineconeIndexUrl);
            log.info("🔍 Searching for: {}", question);

            Map response = webClient.post()
                    .uri(pineconeIndexUrl + "/records/namespaces/__default__/search")
                    .header("Api-Key", pineconeKey)
                    .header("Content-Type", "application/json")
                    .header("X-Pinecone-API-Version", "2025-04")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.error("❌ Pinecone error [{}]: {}",
                                        clientResponse.statusCode(), body);
                                return Mono.error(new RuntimeException("Pinecone: " + body));
                            })
                    )
                    .bodyToMono(Map.class)
                    .block();

            List<Map> hits = (List<Map>) ((Map) response.get("result")).get("hits");

            if (hits == null || hits.isEmpty()) {
                log.warn("⚠️ No Pinecone hits for: {}", question);
                return "No relevant information found.";
            }

            StringBuilder context = new StringBuilder();
            for (Map hit : hits) {
                Map fields = (Map) hit.get("fields");
                context.append("Q: ").append(fields.get("question"))
                        .append("\nA: ").append(fields.get("answer"))
                        .append("\n\n");
            }
            log.info("✅ Got {} Pinecone hits", hits.size());
            return context.toString();

        } catch (Exception e) {
            log.error("❌ findRelevantContext failed: {}", e.getMessage(), e);
            throw new RuntimeException("Pinecone search failed: " + e.getMessage());
        }
    }

    public String generateAnswer(String question, String context,
                                 List<Map<String, String>> history) {
        try {
            String systemPrompt = "You are a helpful assistant for Kandy Dawson Bungalow guest house.\n"
                    + "Only answer questions about the property, amenities, bookings, and local area.\n"
                    + "Use ONLY the knowledge base below. If the question is not covered, reply:\n"
                    + "'Sorry, I can only answer questions about Kandy Dawson Bungalow!'\n\n"
                    + "Knowledge Base:\n" + context;

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));

            if (history != null) {
                for (Map<String, String> turn : history) {
                    messages.add(Map.of(
                            "role",    turn.get("role"),
                            "content", turn.get("content")
                    ));
                }
            }
            messages.add(Map.of("role", "user", "content", question));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model",       groqModel);
            requestBody.put("max_tokens",  300);
            requestBody.put("temperature", 0.7);
            requestBody.put("messages",    messages);

            log.info("🤖 Calling Groq: {}", groqModel);

            Map response = webClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + groqKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.error("❌ Groq error [{}]: {}",
                                        clientResponse.statusCode(), body);
                                return Mono.error(new RuntimeException("Groq: " + body));
                            })
                    )
                    .bodyToMono(Map.class)
                    .block();

            List<Map> choices = (List<Map>) response.get("choices");
            Map message = (Map) choices.get(0).get("message");
            log.info("✅ Groq responded successfully");
            return (String) message.get("content");

        } catch (Exception e) {
            log.error("❌ generateAnswer failed: {}", e.getMessage(), e);
            throw new RuntimeException("Groq call failed: " + e.getMessage());
        }
    }

    public String getAnswer(String question, List<Map<String, String>> history) {
        String context = findRelevantContext(question);
        return generateAnswer(question, context, history);
    }
}

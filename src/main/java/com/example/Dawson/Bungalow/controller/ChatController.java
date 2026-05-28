package com.example.Dawson.Bungalow.controller;

import com.example.Dawson.Bungalow.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        List<Map<String, String>> history =
                (List<Map<String, String>>) body.getOrDefault("history", List.of());

        if (question == null || question.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Question is required"));

        try {
            String answer = chatService.getAnswer(question, history);
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {

            log.error("❌ /chat error: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("answer", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
package com.interviewai.controller;

import com.interviewai.dto.LearningMessageRequest;
import com.interviewai.dto.LearningMessageResponse;
import com.interviewai.dto.StartLearningRequest;
import com.interviewai.dto.StartLearningResponse;
import com.interviewai.entity.LearningSession;
import com.interviewai.entity.User;
import com.interviewai.service.LearningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    @Autowired
    private LearningService learningService;

    @PostMapping("/start")
    public ResponseEntity<StartLearningResponse> startSession(
            @RequestBody StartLearningRequest request, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(learningService.startSession(request, user));
    }

    @PostMapping("/message")
    public ResponseEntity<LearningMessageResponse> sendMessage(
            @RequestBody LearningMessageRequest request, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(learningService.sendMessage(request.getSessionId(), request.getContent(), user));
    }

    @GetMapping("/topics")
    public ResponseEntity<List<String>> getSuggestedTopics(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(learningService.getSuggestedTopics(user));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<LearningSession>> getSessions(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(learningService.getUserSessions(user));
    }

    @GetMapping("/session/{id}/messages")
    public ResponseEntity<?> getMessages(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(learningService.getSessionMessages(id, user.getId()));
    }
}

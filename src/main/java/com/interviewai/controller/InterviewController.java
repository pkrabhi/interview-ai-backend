package com.interviewai.controller;

import com.interviewai.dto.SendMessageRequest;
import com.interviewai.dto.SendMessageResponse;
import com.interviewai.dto.StartSessionRequest;
import com.interviewai.dto.StartSessionResponse;
import com.interviewai.entity.InterviewSession;
import com.interviewai.entity.Message;
import com.interviewai.entity.User;
import com.interviewai.service.InterviewService;
import com.interviewai.service.TranscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private TranscriptionService transcriptionService;

    @PostMapping("/start")
    public ResponseEntity<StartSessionResponse> startSession(
            @RequestBody StartSessionRequest request,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        StartSessionResponse response = interviewService.startSession(request, user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/message")
    public ResponseEntity<SendMessageResponse> sendMessage(
            @RequestBody SendMessageRequest request,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        SendMessageResponse response = interviewService.sendMessage(
                request.getSessionId(), request.getContent(), user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<InterviewSession>> getSessions(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(interviewService.getUserSessions(user));
    }

    @GetMapping("/session/{id}/messages")
    public ResponseEntity<?> getMessages(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(interviewService.getSessionMessages(id, user.getId()));
    }

    @PostMapping("/end")
    public ResponseEntity<Void> endSession(
            @RequestParam Long sessionId,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        interviewService.endSessionEarly(sessionId, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transcribe")
    public ResponseEntity<?> transcribe(@RequestParam("audio") MultipartFile audio) {
        try {
            String text = transcriptionService.transcribe(audio.getBytes(), audio.getOriginalFilename());
            return ResponseEntity.ok(java.util.Collections.singletonMap("text", text));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

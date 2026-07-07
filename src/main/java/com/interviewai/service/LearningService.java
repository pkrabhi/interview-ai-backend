package com.interviewai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewai.dto.LearningMessageResponse;
import com.interviewai.dto.StartLearningRequest;
import com.interviewai.dto.StartLearningResponse;
import com.interviewai.entity.LearningMessage;
import com.interviewai.entity.LearningSession;
import com.interviewai.entity.Report;
import com.interviewai.entity.User;
import com.interviewai.repository.LearningMessageRepository;
import com.interviewai.repository.LearningSessionRepository;
import com.interviewai.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class LearningService {

    private static final Logger log = LoggerFactory.getLogger(LearningService.class);

    @Autowired
    private LearningSessionRepository sessionRepository;

    @Autowired
    private LearningMessageRepository messageRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private AIService aiService;

    private final ObjectMapper mapper = new ObjectMapper();

    public StartLearningResponse startSession(StartLearningRequest request, User user) {
        LearningSession session = new LearningSession();
        session.setUser(user);
        session.setTopic(request.getTopic());
        session = sessionRepository.save(session);

        String systemPrompt = buildTutorPrompt(session, null);
        String openingMessage = aiService.getInterviewerResponse(java.util.Collections.emptyList(), systemPrompt);
        saveMessage(session, "tutor", openingMessage, 1);

        return new StartLearningResponse(session.getId(), openingMessage);
    }

    public LearningMessageResponse sendMessage(Long sessionId, String userContent, User user) {
        LearningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Access denied");

        int currentCount = messageRepository.countBySessionId(sessionId);
        saveMessage(session, "user", userContent, currentCount + 1);

        // expo/backend messages reuse the interviewer/candidate role convention AIService
        // expects, so translate tutor/user history into that shape via buildTutorPrompt's
        // caller — AIService only cares about alternating roles, not their literal names.
        List<com.interviewai.entity.Message> history = toGenericHistory(
                messageRepository.findBySessionIdOrderBySequenceAsc(sessionId));
        String systemPrompt = buildTutorPrompt(session, userContent);
        String aiResponse = aiService.getInterviewerResponse(history, systemPrompt);

        saveMessage(session, "tutor", aiResponse, currentCount + 2);
        return new LearningMessageResponse(aiResponse);
    }

    public List<LearningSession> getUserSessions(User user) {
        try {
            return sessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        } catch (Exception e) {
            // This list is decorative (LearnScreen shows it as a "resume" shortcut) — never
            // let a query issue here surface as an unexpected status to the client.
            log.error("Failed to load learning sessions for user {}: {}", user.getId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public List<LearningMessage> getSessionMessages(Long sessionId, Long userId) {
        LearningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getUser().getId().equals(userId))
            throw new RuntimeException("Access denied");
        return messageRepository.findBySessionIdOrderBySequenceAsc(sessionId);
    }

    /**
     * Pulls "next_topics" and "improvements" off the user's recent interview reports — no
     * extra AI call needed, this data is already generated after every completed interview.
     */
    public List<String> getSuggestedTopics(User user) {
        try {
            List<Report> reports = reportRepository.findBySession_User_IdOrderByCreatedAtDesc(user.getId());
            Set<String> topics = new LinkedHashSet<>();

            for (Report report : reports) {
                if (topics.size() >= 8) break;
                addTopicsFromJson(topics, report.getNextTopics());
                addTopicsFromJson(topics, report.getImprovements());
            }
            return new ArrayList<>(topics);
        } catch (Exception e) {
            log.error("Failed to load suggested topics for user {}: {}", user.getId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private void addTopicsFromJson(Set<String> topics, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonNode arr = mapper.readTree(json);
            if (arr.isArray()) {
                arr.forEach(node -> {
                    if (topics.size() < 8) topics.add(node.asText());
                });
            }
        } catch (Exception ignored) { /* malformed/legacy data — skip */ }
    }

    private void saveMessage(LearningSession session, String role, String content, int sequence) {
        LearningMessage message = new LearningMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        message.setSequence(sequence);
        messageRepository.save(message);
    }

    private List<com.interviewai.entity.Message> toGenericHistory(List<LearningMessage> learningMessages) {
        List<com.interviewai.entity.Message> history = new ArrayList<>();
        for (LearningMessage lm : learningMessages) {
            com.interviewai.entity.Message m = new com.interviewai.entity.Message();
            // AIService/GroqService only inspect role to decide assistant vs user — "tutor"
            // plays the same part "interviewer" does in the interview flow.
            m.setRole("tutor".equals(lm.getRole()) ? "interviewer" : "candidate");
            m.setContent(lm.getContent());
            history.add(m);
        }
        return history;
    }

    private String buildTutorPrompt(LearningSession session, String latestUserMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are Aryan, a friendly senior mentor helping an Indian IT job seeker prepare for interviews.\n\n");

        if (session.getTopic() != null && !session.getTopic().isEmpty()) {
            prompt.append("TOPIC TO TEACH: ").append(session.getTopic()).append("\n");
        } else {
            prompt.append("The candidate can ask about anything interview-related — respond to whatever they bring up.\n");
        }

        prompt.append("\nYOUR BEHAVIOR RULES:\n");
        prompt.append("1. Explain concepts clearly with a short concrete example — assume the candidate is preparing for a real interview, not writing an academic paper.\n");
        prompt.append("2. Keep each response focused and conversational — a few sentences to a short paragraph, not a wall of text.\n");
        prompt.append("3. After explaining, invite a follow-up: ask if they'd like to go deeper, see another example, or move to a related topic.\n");
        prompt.append("4. If they ask a question, answer it directly first before adding extra context.\n");
        prompt.append("5. Be encouraging and conversational — you're a mentor, not a lecturer.\n");
        prompt.append("6. Never reveal you are an AI.\n\n");

        if (session.getTopic() != null && !session.getTopic().isEmpty() && latestUserMessage == null) {
            prompt.append("START: Warmly introduce the topic and give a clear, practical explanation of it.");
        } else if (latestUserMessage == null) {
            prompt.append("START: Greet the candidate and ask what they'd like to learn or ask about today.");
        }

        return prompt.toString();
    }
}

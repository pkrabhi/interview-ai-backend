package com.interviewai.service;

import com.interviewai.dto.SendMessageResponse;
import com.interviewai.dto.StartSessionRequest;
import com.interviewai.dto.StartSessionResponse;
import com.interviewai.entity.InterviewSession;
import com.interviewai.entity.Message;
import com.interviewai.entity.User;
import com.interviewai.repository.InterviewSessionRepository;
import com.interviewai.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewService {

    @Autowired
    private InterviewSessionRepository sessionRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private AIService aiService;

    @Autowired
    private ReportService reportService;

    public StartSessionResponse startSession(StartSessionRequest request, User user) {
        // Check free plan limit
        if ("FREE".equals(user.getPlan()) && user.getSessionsUsed() >= 2) {
            throw new RuntimeException("Free plan limit reached. Upgrade to Pro for unlimited interviews.");
        }

        // Create session record
        InterviewSession session = new InterviewSession();
        session.setUser(user);
        session.setRole(request.getRole());
        session.setLevel(request.getLevel());
        session.setInterviewType(request.getInterviewType());
        session.setJdText(request.getJdText());
        session.setResumeSummary(request.getResumeSummary());
        session.setStatus("ACTIVE");
        session = sessionRepository.save(session);

        // Get opening question from AI (empty history = first message)
        String systemPrompt = buildSystemPrompt(session);
        List<Message> emptyHistory = java.util.Collections.emptyList();
        String openingMessage = aiService.getInterviewerResponse(emptyHistory, systemPrompt);

        // Save opening message to DB
        saveMessage(session, "interviewer", openingMessage, 1);

        return new StartSessionResponse(session.getId(), openingMessage);
    }

    public SendMessageResponse sendMessage(Long sessionId, String userContent, User user) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!"ACTIVE".equals(session.getStatus())) {
            throw new RuntimeException("Session is already completed");
        }

        // Get current message count to determine sequence number
        int currentCount = messageRepository.countBySessionId(sessionId);

        // Save user's answer
        saveMessage(session, "candidate", userContent, currentCount + 1);

        // Fetch full history and get AI follow-up
        List<Message> history = messageRepository.findBySessionIdOrderBySequenceAsc(sessionId);
        String systemPrompt = buildSystemPrompt(session);
        String aiResponse = aiService.getInterviewerResponse(history, systemPrompt);

        boolean interviewComplete = aiResponse.contains("INTERVIEW_COMPLETE");

        if (interviewComplete) {
            session.setStatus("COMPLETED");
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);
            aiResponse = aiResponse.replace("INTERVIEW_COMPLETE", "").trim();
            // Generate report asynchronously — pass ID only, service fetches fresh
            final Long completedSessionId = session.getId();
            new Thread(() -> reportService.generateReportById(completedSessionId)).start();
        }

        // Save AI response
        saveMessage(session, "interviewer", aiResponse, currentCount + 2);

        return new SendMessageResponse(aiResponse, interviewComplete, sessionId);
    }

    public void endSessionEarly(Long sessionId, User user) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!"ACTIVE".equals(session.getStatus())) return;
        session.setStatus("COMPLETED");
        session.setCompletedAt(java.time.LocalDateTime.now());
        sessionRepository.save(session);
        final Long completedId = session.getId();
        new Thread(() -> reportService.generateReportById(completedId)).start();
    }

    public List<InterviewSession> getUserSessions(User user) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public List<Message> getSessionMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderBySequenceAsc(sessionId);
    }

    private void saveMessage(InterviewSession session, String role, String content, int sequence) {
        Message message = new Message();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        message.setSequence(sequence);
        messageRepository.save(message);
    }

    private static final java.util.Map<String, String[]> TOPIC_SEEDS = new java.util.HashMap<>();
    static {
        TOPIC_SEEDS.put("java", new String[]{
            "JVM internals and memory management",
            "multithreading and concurrency",
            "collections framework and generics",
            "exception handling and design patterns",
            "Spring Boot and dependency injection",
            "database transactions and JPA",
            "REST API design and best practices",
            "microservices and inter-service communication"
        });
        TOPIC_SEEDS.put("fullstack", new String[]{
            "frontend state management",
            "REST vs GraphQL API design",
            "database design and indexing",
            "authentication and JWT",
            "Docker and deployment",
            "performance optimization"
        });
        TOPIC_SEEDS.put("data", new String[]{
            "SQL query optimization",
            "ETL pipeline design",
            "data warehousing concepts",
            "Python for data processing",
            "Spark and big data tools",
            "data modeling"
        });
        TOPIC_SEEDS.put("devops", new String[]{
            "CI/CD pipeline design",
            "Kubernetes and container orchestration",
            "cloud infrastructure (AWS/Azure/GCP)",
            "monitoring and alerting",
            "infrastructure as code",
            "security and access control"
        });
        TOPIC_SEEDS.put("react", new String[]{
            "React hooks and lifecycle",
            "state management (Redux/Zustand)",
            "performance optimization in React",
            "component design patterns",
            "TypeScript with React",
            "testing React components"
        });
        TOPIC_SEEDS.put("hr", new String[]{
            "your biggest professional achievement",
            "a conflict you resolved at work",
            "your approach to learning new technologies",
            "how you handle tight deadlines",
            "your leadership experience",
            "your career goals"
        });
    }

    private String buildSystemPrompt(InterviewSession session) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are Aryan, a Senior Technical Interviewer at a top Indian IT company.\n\n");
        prompt.append("ROLE: ").append(session.getRole()).append(" Developer\n");
        prompt.append("LEVEL: ").append(session.getLevel()).append("\n");
        prompt.append("INTERVIEW TYPE: ").append(session.getInterviewType()).append("\n");

        if (session.getJdText() != null && !session.getJdText().isEmpty()) {
            prompt.append("JOB DESCRIPTION: ").append(session.getJdText()).append("\n");
        }

        if (session.getResumeSummary() != null && !session.getResumeSummary().isEmpty()) {
            prompt.append("CANDIDATE'S RESUME SUMMARY: ").append(session.getResumeSummary()).append("\n");
            prompt.append("IMPORTANT: Use the resume summary to ask about the candidate's ACTUAL projects, skills, and experiences. Reference specific things from their resume.\n");
        }

        // Random topic seed so each session starts from a different angle
        String[] topics = TOPIC_SEEDS.getOrDefault(session.getRole().toLowerCase(),
                TOPIC_SEEDS.get("java"));
        String seed = topics[(int) (Math.random() * topics.length)];
        prompt.append("STARTING TOPIC: Begin the interview by asking about ").append(seed).append(".\n");

        prompt.append("\nYOUR BEHAVIOR RULES:\n");
        prompt.append("1. Ask ONE question at a time. Never ask multiple questions in one message.\n");
        prompt.append("2. Listen carefully to the candidate's answer.\n");
        prompt.append("3. If the answer is shallow or incomplete → ask a probing follow-up.\n");
        prompt.append("4. If the answer is wrong → probe further, don't correct immediately.\n");
        prompt.append("5. If the answer is excellent → acknowledge briefly and move to next topic.\n");
        prompt.append("6. After 6-8 candidate responses → say exactly: INTERVIEW_COMPLETE then give a one-line closing.\n");
        prompt.append("7. Keep questions India-IT-market relevant (Spring Boot, microservices, common interview topics).\n");
        prompt.append("8. Stay in character. You are a human interviewer, not an AI assistant.\n");
        prompt.append("9. Never reveal you are an AI.\n\n");
        prompt.append("START: Greet the candidate warmly and ask your first question on the starting topic.");

        return prompt.toString();
    }
}

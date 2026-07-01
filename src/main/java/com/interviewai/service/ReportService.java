package com.interviewai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewai.entity.InterviewSession;
import com.interviewai.entity.Message;
import com.interviewai.entity.Report;
import com.interviewai.repository.MessageRepository;
import com.interviewai.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private AIService aiService;

    @Autowired
    private com.interviewai.repository.InterviewSessionRepository sessionRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    public void generateReportById(Long sessionId) {
        try {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
            generateReport(session);
        } catch (Exception e) {
            log.error("Background report generation failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    public Report generateReport(InterviewSession session) {
        List<Message> messages = messageRepository.findBySessionIdOrderBySequenceAsc(session.getId());

        // Build transcript for AI evaluation
        StringBuilder transcript = new StringBuilder();
        for (Message msg : messages) {
            String speaker = msg.getRole().equals("interviewer") ? "Interviewer" : "Candidate";
            transcript.append(speaker).append(": ").append(msg.getContent()).append("\n\n");
        }

        String evaluationPrompt = buildEvaluationPrompt(session, transcript.toString());

        String evaluationJson;
        try {
            evaluationJson = aiService.getInterviewerResponse(
                java.util.Collections.emptyList(), evaluationPrompt);
        } catch (Exception e) {
            log.error("Failed to generate AI evaluation: {}", e.getMessage());
            evaluationJson = getDefaultEvaluation();
        }

        String qaReviewJson = buildQaReview(session, messages);

        return saveReport(session, evaluationJson, qaReviewJson);
    }

    /** Pairs each interviewer question with the candidate's next answer, then asks the AI for a model answer per question. */
    private String buildQaReview(InterviewSession session, List<Message> messages) {
        List<String> questions = new ArrayList<>();
        List<String> candidateAnswers = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (!"interviewer".equals(msg.getRole())) continue;
            String answer = null;
            if (i + 1 < messages.size() && "candidate".equals(messages.get(i + 1).getRole())) {
                answer = messages.get(i + 1).getContent();
            }
            questions.add(msg.getContent());
            candidateAnswers.add(answer);
        }

        if (questions.isEmpty()) return "[]";

        String idealAnswersJson;
        try {
            idealAnswersJson = aiService.getInterviewerResponse(
                java.util.Collections.emptyList(), buildIdealAnswersPrompt(session, questions));
        } catch (Exception e) {
            log.error("Failed to generate ideal answers: {}", e.getMessage());
            idealAnswersJson = null;
        }

        List<String> idealAnswers = new ArrayList<>();
        try {
            if (idealAnswersJson != null) {
                ArrayNode arr = (ArrayNode) mapper.readTree(idealAnswersJson);
                for (int i = 0; i < arr.size(); i++) idealAnswers.add(arr.get(i).asText());
            }
        } catch (Exception e) {
            log.error("Error parsing ideal answers JSON: {}", e.getMessage());
        }

        ArrayNode review = mapper.createArrayNode();
        for (int i = 0; i < questions.size(); i++) {
            ObjectNode entry = mapper.createObjectNode();
            entry.put("question", questions.get(i));
            entry.put("candidateAnswer", candidateAnswers.get(i) != null ? candidateAnswers.get(i) : "No answer provided");
            entry.put("idealAnswer", i < idealAnswers.size() ? idealAnswers.get(i) : "Not available");
            review.add(entry);
        }
        return review.toString();
    }

    private String buildIdealAnswersPrompt(InterviewSession session, List<String> questions) {
        StringBuilder qList = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            qList.append(i + 1).append(". ").append(questions.get(i)).append("\n");
        }
        return "You are an expert senior technical interviewer at a top Indian IT company.\n" +
                "ROLE: " + session.getRole() + " Developer | LEVEL: " + session.getLevel() + "\n\n" +
                "For each of the following interview questions, write a concise, correct model answer " +
                "(3-5 sentences) that a strong candidate would give.\n\n" +
                "QUESTIONS:\n" + qList + "\n" +
                "Respond with ONLY a valid JSON array of strings, one answer per question, in the same order, " +
                "no markdown, no explanation. Example: [\"answer 1\", \"answer 2\"]";
    }

    public Report getReport(Long sessionId) {
        return reportRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Report not found for session: " + sessionId));
    }

    private Report saveReport(InterviewSession session, String evaluationJson, String qaReviewJson) {
        try {
            ObjectNode eval = (ObjectNode) mapper.readTree(evaluationJson);

            Report report = new Report();
            report.setSession(session);
            report.setOverallScore(eval.has("overall_score") ? eval.get("overall_score").asInt(60) : 60);
            report.setTechnicalScore(eval.has("technical_score") ? eval.get("technical_score").asInt(60) : 60);
            report.setCommunicationScore(eval.has("communication_score") ? eval.get("communication_score").asInt(60) : 60);
            report.setProblemSolvingScore(eval.has("problem_solving_score") ? eval.get("problem_solving_score").asInt(60) : 60);
            report.setBestPracticesScore(eval.has("best_practices_score") ? eval.get("best_practices_score").asInt(60) : 60);
            report.setStrengths(eval.has("strengths") ? eval.get("strengths").toString() : "[\"Good communication\"]");
            report.setImprovements(eval.has("improvements") ? eval.get("improvements").toString() : "[\"Study core concepts\"]");
            report.setNextTopics(eval.has("next_topics") ? eval.get("next_topics").toString() : "[\"Spring Boot internals\"]");
            report.setQaReview(qaReviewJson != null ? qaReviewJson : "[]");

            return reportRepository.save(report);
        } catch (Exception e) {
            log.error("Error parsing evaluation JSON: {}", e.getMessage());
            Report report = new Report();
            report.setSession(session);
            report.setOverallScore(60);
            report.setTechnicalScore(60);
            report.setCommunicationScore(65);
            report.setProblemSolvingScore(55);
            report.setBestPracticesScore(60);
            report.setStrengths("[\"Demonstrated good communication skills\"]");
            report.setImprovements("[\"Review core Java concepts\",\"Practice system design\"]");
            report.setNextTopics("[\"Spring Boot Auto-configuration\",\"Microservices patterns\"]");
            report.setQaReview(qaReviewJson != null ? qaReviewJson : "[]");
            return reportRepository.save(report);
        }
    }

    private String buildEvaluationPrompt(InterviewSession session, String transcript) {
        return "You are an expert senior technical interviewer at a top Indian IT company.\n" +
                "Evaluate the following mock interview transcript fairly and constructively.\n\n" +
                "ROLE: " + session.getRole() + " Developer | LEVEL: " + session.getLevel() + "\n\n" +
                "TRANSCRIPT:\n" + transcript + "\n\n" +
                "SCORING GUIDELINES:\n" +
                "- 80-100: Excellent answers, deep understanding, clear communication\n" +
                "- 60-79: Good answers with some gaps, mostly correct\n" +
                "- 40-59: Partial knowledge, answered some parts correctly\n" +
                "- 20-39: Very limited answers, mostly incorrect or 'I don't know'\n" +
                "- 0-19: No meaningful answers given\n\n" +
                "IMPORTANT RULES:\n" +
                "- Be fair and accurate. Do NOT inflate or deflate scores.\n" +
                "- Even if answers are weak, find at least 1-2 genuine strengths (e.g., honesty, willingness to learn, communication style).\n" +
                "- Improvements must be specific and actionable for an Indian IT job seeker.\n" +
                "- next_topics should be the exact concepts the candidate struggled with.\n" +
                "- If the interview was very short (less than 3 answers), note that in improvements.\n\n" +
                "Respond with ONLY a valid JSON object, no markdown, no explanation:\n" +
                "{\n" +
                "  \"overall_score\": <0-100>,\n" +
                "  \"technical_score\": <0-100>,\n" +
                "  \"communication_score\": <0-100>,\n" +
                "  \"problem_solving_score\": <0-100>,\n" +
                "  \"best_practices_score\": <0-100>,\n" +
                "  \"strengths\": [\"specific strength 1\", \"specific strength 2\"],\n" +
                "  \"improvements\": [\"specific improvement 1\", \"specific improvement 2\", \"specific improvement 3\"],\n" +
                "  \"next_topics\": [\"topic 1\", \"topic 2\", \"topic 3\"]\n" +
                "}";
    }

    private String getDefaultEvaluation() {
        return "{\"overall_score\":60,\"technical_score\":60,\"communication_score\":65," +
                "\"problem_solving_score\":55,\"best_practices_score\":60," +
                "\"strengths\":[\"Good communication\",\"Showed enthusiasm\"]," +
                "\"improvements\":[\"Deepen core Java knowledge\",\"Practice system design\"]," +
                "\"next_topics\":[\"Spring Boot internals\",\"Microservices\",\"Design patterns\"]}";
    }
}

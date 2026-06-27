package com.interviewai.service;

import com.interviewai.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    @Autowired
    private GroqService groqService;

    @Autowired
    private NvidiaService nvidiaService;

    public String getInterviewerResponse(List<Message> history, String systemPrompt) {
        try {
            return groqService.chat(history, systemPrompt);
        } catch (Exception e) {
            log.warn("Groq failed: {}. Trying NVIDIA NIM...", e.getMessage());
            try {
                return nvidiaService.chat(history, systemPrompt);
            } catch (Exception e2) {
                log.error("Both AI providers failed: {}", e2.getMessage());
                return "That's interesting. Could you elaborate further on your last point?";
            }
        }
    }
}

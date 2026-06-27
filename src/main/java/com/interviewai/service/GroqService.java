package com.interviewai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewai.entity.Message;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    @Value("${groq.max.tokens}")
    private int maxTokens;

    private final ObjectMapper mapper = new ObjectMapper();

    public String chat(List<Message> history, String systemPrompt) throws Exception {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", 0.7);

        ArrayNode messages = requestBody.putArray("messages");

        // System prompt — sets AI persona and behavior rules
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);

        // Full conversation history so AI remembers what was said
        for (Message msg : history) {
            ObjectNode m = messages.addObject();
            m.put("role", msg.getRole().equals("interviewer") ? "assistant" : "user");
            m.put("content", msg.getContent());
        }

        HttpPost request = new HttpPost(apiUrl);
        request.setHeader("Authorization", "Bearer " + apiKey);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(mapper.writeValueAsString(requestBody), "UTF-8"));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {

            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode json = mapper.readTree(responseBody);

            if (json.has("error")) {
                throw new RuntimeException("Groq error: " + json.get("error").get("message").asText());
            }

            return json.get("choices").get(0).get("message").get("content").asText();
        }
    }
}

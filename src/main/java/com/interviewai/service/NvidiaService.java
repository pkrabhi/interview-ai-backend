package com.interviewai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewai.entity.Message;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NvidiaService {

    @Value("${nvidia.api.key}")
    private String apiKey;

    @Value("${nvidia.api.url}")
    private String apiUrl;

    @Value("${nvidia.model}")
    private String model;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectTimeout(10_000)
            .setSocketTimeout(40_000)
            .build();

    public String chat(List<Message> history, String systemPrompt) throws Exception {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 500);
        requestBody.put("temperature", 0.7);

        ArrayNode messages = requestBody.putArray("messages");

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);

        for (Message msg : history) {
            ObjectNode m = messages.addObject();
            m.put("role", msg.getRole().equals("interviewer") ? "assistant" : "user");
            m.put("content", msg.getContent());
        }

        HttpPost request = new HttpPost(apiUrl);
        request.setConfig(REQUEST_CONFIG);
        request.setHeader("Authorization", "Bearer " + apiKey);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(mapper.writeValueAsString(requestBody), "UTF-8"));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {

            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode json = mapper.readTree(responseBody);

            if (json.has("error")) {
                throw new RuntimeException("NVIDIA error: " + json.get("error").get("message").asText());
            }

            return json.get("choices").get(0).get("message").get("content").asText();
        }
    }
}

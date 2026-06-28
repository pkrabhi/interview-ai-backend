package com.interviewai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ResumeService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    public String extractAndSummarise(byte[] pdfBytes) throws Exception {
        // Extract text from PDF
        String resumeText;
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            resumeText = stripper.getText(doc);
        }

        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new RuntimeException("Could not extract text from PDF. Make sure it is not a scanned image.");
        }

        // Truncate to avoid huge prompts (keep first 4000 chars)
        if (resumeText.length() > 4000) {
            resumeText = resumeText.substring(0, 4000);
        }

        // Ask Groq to summarise skills, projects, experience
        String prompt = "You are analysing a candidate's resume for a mock interview app. " +
                "Extract and summarise in 150 words or less:\n" +
                "1. Key technical skills and technologies\n" +
                "2. Notable projects (name + what it does)\n" +
                "3. Work experience (companies, roles)\n" +
                "4. Years of experience\n\n" +
                "Resume text:\n" + resumeText + "\n\n" +
                "Respond with a concise summary only. No headings, no bullet points, just plain text.";

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "llama-3.3-70b-versatile");
        body.put("max_tokens", 300);
        body.put("temperature", 0.3);

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);

        HttpPost request = new HttpPost(groqApiUrl);
        request.setHeader("Authorization", "Bearer " + groqApiKey);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(mapper.writeValueAsString(body), "UTF-8"));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode json = mapper.readTree(responseBody);
            if (json.has("error")) {
                throw new RuntimeException("AI error: " + json.get("error").get("message").asText());
            }
            return json.get("choices").get(0).get("message").get("content").asText().trim();
        }
    }
}

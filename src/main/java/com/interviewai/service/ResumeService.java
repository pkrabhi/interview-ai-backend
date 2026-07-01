package com.interviewai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    @Autowired
    private AIService aiService;

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

        // Truncate to avoid huge prompts (keep first 6000 chars — enough for most resumes)
        if (resumeText.length() > 6000) {
            resumeText = resumeText.substring(0, 6000);
        }

        String prompt = buildExtractionPrompt(resumeText);

        // Routes through AIService's Groq -> NVIDIA fallback, same as the interview chat
        // and report generation, instead of calling Groq directly with no fallback.
        String aiJson = aiService.getInterviewerResponse(java.util.Collections.emptyList(), prompt);

        return normalise(aiJson);
    }

    private String buildExtractionPrompt(String resumeText) {
        return "You are analysing a candidate's resume for a mock interview app.\n" +
                "Extract the following from the resume text and respond with ONLY a valid JSON object, " +
                "no markdown, no explanation:\n" +
                "{\n" +
                "  \"summary\": \"<1-2 sentence overview of the candidate>\",\n" +
                "  \"skills\": [\"skill1\", \"skill2\", ...],\n" +
                "  \"projects\": [{\"name\": \"<project name>\", \"description\": \"<one line on what it does/uses>\"}],\n" +
                "  \"companies\": [{\"name\": \"<company>\", \"role\": \"<role>\", \"duration\": \"<e.g. 2 years>\"}],\n" +
                "  \"yearsExperience\": <number>\n" +
                "}\n\n" +
                "If a field can't be determined, use an empty array or 0. Keep skills to the most relevant 10, " +
                "projects to the most notable 5.\n\n" +
                "RESUME TEXT:\n" + resumeText;
    }

    /**
     * Validates the AI's JSON response and fills in any missing fields with safe defaults so a
     * malformed or fallback (non-JSON) AI response never surfaces as a hard upload failure —
     * the candidate still gets a resume-aware interview, just with less detail attached.
     */
    private String normalise(String aiJson) {
        try {
            JsonNode node = mapper.readTree(aiJson);
            ObjectNode out = mapper.createObjectNode();
            out.put("summary", node.path("summary").asText(""));
            out.set("skills", node.has("skills") && node.get("skills").isArray() ? node.get("skills") : mapper.createArrayNode());
            out.set("projects", node.has("projects") && node.get("projects").isArray() ? node.get("projects") : mapper.createArrayNode());
            out.set("companies", node.has("companies") && node.get("companies").isArray() ? node.get("companies") : mapper.createArrayNode());
            out.put("yearsExperience", node.path("yearsExperience").asInt(0));
            return mapper.writeValueAsString(out);
        } catch (Exception e) {
            log.warn("Could not parse resume extraction JSON, using minimal fallback: {}", e.getMessage());
            return "{\"summary\":\"Resume uploaded — details will come up naturally during the interview.\"," +
                    "\"skills\":[],\"projects\":[],\"companies\":[],\"yearsExperience\":0}";
        }
    }
}

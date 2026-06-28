package com.interviewai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

@Service
public class TranscriptionService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    public String transcribe(byte[] audioBytes, String filename) throws Exception {
        String boundary = "----Boundary" + System.currentTimeMillis();
        URL url = new URL("https://api.groq.com/openai/v1/audio/transcriptions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + groqApiKey);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream out = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true)) {

            // audio file part
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                  .append(filename != null ? filename : "audio.m4a").append("\"\r\n");
            writer.append("Content-Type: audio/m4a\r\n\r\n");
            writer.flush();
            out.write(audioBytes);
            out.flush();
            writer.append("\r\n");

            // model part
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
            writer.append("whisper-large-v3\r\n");

            // language part
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
            writer.append("en\r\n");

            writer.append("--").append(boundary).append("--\r\n");
            writer.flush();
        }

        int status = conn.getResponseCode();
        InputStream is = status == 200 ? conn.getInputStream() : conn.getErrorStream();
        java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
        String body = scanner.hasNext() ? scanner.next() : "";

        com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);

        if (node.has("error")) {
            throw new RuntimeException("Groq Whisper error: " + node.get("error").get("message").asText());
        }
        return node.path("text").asText();
    }
}

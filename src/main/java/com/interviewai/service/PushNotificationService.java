package com.interviewai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Sends push notifications via Expo's push service (https://exp.host/--/api/v2/push/send).
 * Only works for tokens obtained from a real device via a native (EAS) build — Expo push
 * tokens aren't available on the web platform.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final ObjectMapper mapper = new ObjectMapper();

    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectTimeout(10_000)
            .setSocketTimeout(15_000)
            .build();

    public void send(String expoPushToken, String title, String body, Map<String, Object> data) {
        if (expoPushToken == null || expoPushToken.isEmpty()) return;
        if (!expoPushToken.startsWith("ExponentPushToken")) {
            log.warn("Skipping push — not a valid Expo push token: {}", expoPushToken);
            return;
        }

        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("to", expoPushToken);
            payload.put("title", title);
            payload.put("body", body);
            payload.put("sound", "default");
            if (data != null) {
                payload.set("data", mapper.valueToTree(data));
            }

            HttpPost request = new HttpPost(EXPO_PUSH_URL);
            request.setConfig(REQUEST_CONFIG);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Accept", "application/json");
            request.setEntity(new StringEntity(mapper.writeValueAsString(payload), "UTF-8"));

            try (CloseableHttpClient client = HttpClients.createDefault();
                 CloseableHttpResponse response = client.execute(request)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                JsonNode json = mapper.readTree(responseBody);
                JsonNode dataNode = json.path("data");
                if (dataNode.has("status") && "error".equals(dataNode.get("status").asText())) {
                    log.warn("Expo push error: {}", dataNode.path("message").asText());
                }
            }
        } catch (Exception e) {
            // Push failures should never break the caller's main flow (report generation,
            // scheduled reminders, etc.) — log and move on.
            log.warn("Failed to send push notification: {}", e.getMessage());
        }
    }
}

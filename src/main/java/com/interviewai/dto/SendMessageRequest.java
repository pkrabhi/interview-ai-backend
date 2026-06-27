package com.interviewai.dto;

import lombok.Data;

@Data
public class SendMessageRequest {
    private Long sessionId;
    private String content;
}

package com.interviewai.dto;

import lombok.Data;

@Data
public class LearningMessageRequest {
    private Long sessionId;
    private String content;
}

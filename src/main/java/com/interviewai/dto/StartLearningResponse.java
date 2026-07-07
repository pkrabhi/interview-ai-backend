package com.interviewai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StartLearningResponse {
    private Long sessionId;
    private String openingMessage;
}

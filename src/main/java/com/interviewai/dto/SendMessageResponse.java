package com.interviewai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SendMessageResponse {
    private String aiMessage;
    private boolean interviewComplete;
    private Long sessionId;
}

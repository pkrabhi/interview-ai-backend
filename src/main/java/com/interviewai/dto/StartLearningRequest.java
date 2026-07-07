package com.interviewai.dto;

import lombok.Data;

@Data
public class StartLearningRequest {
    private String topic; // optional — empty means a free-form "ask anything" session
}

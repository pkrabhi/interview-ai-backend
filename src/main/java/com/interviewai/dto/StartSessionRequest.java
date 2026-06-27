package com.interviewai.dto;

import lombok.Data;

@Data
public class StartSessionRequest {
    private String role;
    private String level;
    private String interviewType;
    private String jdText;
}

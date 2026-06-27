package com.interviewai.dto;

import lombok.Data;
import java.util.List;

@Data
public class ReportResponse {
    private Long sessionId;
    private int overallScore;
    private int technicalScore;
    private int communicationScore;
    private int problemSolvingScore;
    private int bestPracticesScore;
    private List<String> strengths;
    private List<String> improvements;
    private List<String> nextTopics;
}

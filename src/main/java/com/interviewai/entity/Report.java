package com.interviewai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", unique = true)
    private InterviewSession session;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "technical_score")
    private Integer technicalScore;

    @Column(name = "communication_score")
    private Integer communicationScore;

    @Column(name = "problem_solving_score")
    private Integer problemSolvingScore;

    @Column(name = "best_practices_score")
    private Integer bestPracticesScore;

    @Column(columnDefinition = "TEXT")
    private String strengths;       // JSON array string

    @Column(columnDefinition = "TEXT")
    private String improvements;    // JSON array string

    @Column(name = "next_topics", columnDefinition = "TEXT")
    private String nextTopics;      // JSON array string

    @Column(name = "pdf_path", length = 500)
    private String pdfPath;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}

package com.interviewai.controller;

import com.interviewai.entity.Report;
import com.interviewai.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/{sessionId}")
    public ResponseEntity<Report> getReport(@PathVariable Long sessionId) {
        return ResponseEntity.ok(reportService.getReport(sessionId));
    }
}

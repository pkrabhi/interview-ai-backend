package com.interviewai.controller;

import com.interviewai.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("resume") MultipartFile file) {
        try {
            if (file.isEmpty()) return ResponseEntity.badRequest().body("No file provided");
            if (file.getSize() > 5 * 1024 * 1024) return ResponseEntity.badRequest().body("File too large. Max 5MB.");
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf"))
                return ResponseEntity.badRequest().body("Only PDF files are accepted.");
            String summary = resumeService.extractAndSummarise(file.getBytes());
            return ResponseEntity.ok(Collections.singletonMap("summary", summary));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

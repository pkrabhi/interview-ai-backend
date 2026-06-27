package com.interviewai.repository;

import com.interviewai.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}

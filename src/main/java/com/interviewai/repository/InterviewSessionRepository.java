package com.interviewai.repository;

import com.interviewai.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<InterviewSession> findByStatusAndCreatedAtBeforeAndReminderSentFalse(String status, LocalDateTime before);
    boolean existsByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);
}

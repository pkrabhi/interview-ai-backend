package com.interviewai.repository;

import com.interviewai.entity.LearningSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningSessionRepository extends JpaRepository<LearningSession, Long> {
    List<LearningSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}

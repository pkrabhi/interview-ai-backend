package com.interviewai.repository;

import com.interviewai.entity.LearningMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningMessageRepository extends JpaRepository<LearningMessage, Long> {
    List<LearningMessage> findBySessionIdOrderBySequenceAsc(Long sessionId);
    int countBySessionId(Long sessionId);
}

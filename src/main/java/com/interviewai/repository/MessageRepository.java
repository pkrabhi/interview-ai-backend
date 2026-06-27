package com.interviewai.repository;

import com.interviewai.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySessionIdOrderBySequenceAsc(Long sessionId);
    int countBySessionId(Long sessionId);
}

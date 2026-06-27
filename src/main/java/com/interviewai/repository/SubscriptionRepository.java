package com.interviewai.repository;

import com.interviewai.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Subscription> findByRazorpayOrderId(String orderId);
}

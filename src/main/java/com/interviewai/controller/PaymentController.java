package com.interviewai.controller;

import com.interviewai.dto.PaymentOrderResponse;
import com.interviewai.dto.PaymentVerifyRequest;
import com.interviewai.entity.Subscription;
import com.interviewai.entity.User;
import com.interviewai.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/order")
    public ResponseEntity<?> createOrder(Authentication auth) {
        try {
            User user = (User) auth.getPrincipal();
            PaymentOrderResponse response = paymentService.createOrder(user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody PaymentVerifyRequest req, Authentication auth) {
        try {
            User user = (User) auth.getPrincipal();
            boolean success = paymentService.verifyAndActivate(user, req);
            Map<String, Object> result = new HashMap<>();
            if (success) {
                result.put("success", true);
                result.put("plan", "PRO");
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("error", "Signature mismatch");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<Subscription>> getHistory(Authentication auth) {
        User user = (User) auth.getPrincipal();
        return ResponseEntity.ok(paymentService.getHistory(user));
    }
}

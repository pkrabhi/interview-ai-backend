package com.interviewai.controller;

import com.interviewai.entity.User;
import com.interviewai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register-token")
    public ResponseEntity<Void> registerToken(@RequestBody Map<String, String> body, Authentication authentication) {
        User authUser = (User) authentication.getPrincipal();
        User user = userRepository.findById(authUser.getId()).orElseThrow(() -> new RuntimeException("User not found"));
        user.setPushToken(body.get("token"));
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/preferences")
    public ResponseEntity<Void> setPreferences(@RequestBody Map<String, Boolean> body, Authentication authentication) {
        User authUser = (User) authentication.getPrincipal();
        User user = userRepository.findById(authUser.getId()).orElseThrow(() -> new RuntimeException("User not found"));
        user.setNotificationsEnabled(body.getOrDefault("enabled", true));
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}

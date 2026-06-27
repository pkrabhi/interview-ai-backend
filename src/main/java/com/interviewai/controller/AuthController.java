package com.interviewai.controller;

import com.interviewai.dto.AuthResponse;
import com.interviewai.dto.GoogleAuthRequest;
import com.interviewai.entity.User;
import com.interviewai.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody GoogleAuthRequest request) {
        try {
            AuthResponse response = authService.googleLogin(request.getIdToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(authService.getCurrentUser(user.getId()));
    }

    @PostMapping("/dev-login")
    public ResponseEntity<AuthResponse> devLogin() {
        try {
            AuthResponse response = authService.devLogin();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }
}

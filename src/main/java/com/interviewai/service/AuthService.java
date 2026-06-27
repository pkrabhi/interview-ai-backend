package com.interviewai.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.interviewai.dto.AuthResponse;
import com.interviewai.entity.User;
import com.interviewai.repository.UserRepository;
import com.interviewai.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${google.client.id}")
    private String googleClientId;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public AuthResponse googleLogin(String token) throws Exception {
        String googleId, email, name, avatarUrl;

        // Always use userinfo endpoint — works for both access tokens and ID tokens
        try {
            java.net.URL url = new java.net.URL("https://www.googleapis.com/oauth2/v3/userinfo");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + token);
            if (conn.getResponseCode() != 200) throw new RuntimeException("Invalid Google token");
            java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
            String body = scanner.hasNext() ? scanner.next() : "";
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            googleId  = node.path("sub").asText();
            email     = node.path("email").asText();
            name      = node.path("name").asText();
            avatarUrl = node.path("picture").asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Google token verification failed: " + e.getMessage());
        }

        Optional<User> existing = userRepository.findByGoogleId(googleId);
        User user = existing.orElseGet(() -> {
            User newUser = new User();
            newUser.setGoogleId(googleId);
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setAvatarUrl(avatarUrl);
            return userRepository.save(newUser);
        });

        String jwt = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(jwt, user.getName(), user.getEmail(), user.getAvatarUrl(), user.getPlan());
    }

    public AuthResponse devLogin() {
        String devEmail = "abhipkr11@gmail.com";
        User user = userRepository.findByEmail(devEmail).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(devEmail);
            newUser.setName("Abhijeet");
            newUser.setPlan("FREE");
            newUser.setSessionsUsed(0);
            return userRepository.save(newUser);
        });
        String jwt = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(jwt, user.getName(), user.getEmail(), user.getAvatarUrl(), user.getPlan());
    }

    public AuthResponse register(String name, String email, String password) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already registered");
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(bcrypt.encode(password));
        user.setPlan("FREE");
        user.setSessionsUsed(0);
        userRepository.save(user);
        String jwt = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(jwt, user.getName(), user.getEmail(), user.getAvatarUrl(), user.getPlan());
    }

    public AuthResponse emailLogin(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email"));
        if (user.getPasswordHash() == null || !bcrypt.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Incorrect password");
        }
        String jwt = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(jwt, user.getName(), user.getEmail(), user.getAvatarUrl(), user.getPlan());
    }

    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}

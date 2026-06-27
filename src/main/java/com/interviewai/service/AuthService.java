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

    public AuthResponse googleLogin(String idToken) throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken googleIdToken = verifier.verify(idToken);
        if (googleIdToken == null) {
            throw new RuntimeException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        String googleId  = payload.getSubject();
        String email     = payload.getEmail();
        String name      = (String) payload.get("name");
        String avatarUrl = (String) payload.get("picture");

        // Create user if first login, otherwise just fetch
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

    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}

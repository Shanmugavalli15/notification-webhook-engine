package com.example.notification.service;

import com.example.notification.dto.request.LoginRequest;
import com.example.notification.dto.request.RegisterRequest;
import com.example.notification.dto.response.AuthResponse;
import com.example.notification.model.AppUser;
import com.example.notification.repository.AppUserRepository;
import com.example.notification.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request must not be null");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email address is already registered");
        }

        AppUser user = AppUser.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .enabled(true)
                .build();

        AppUser savedUser = userRepository.save(Objects.requireNonNull(user));
        log.info("New user registered: {} (id={})", savedUser.getEmail(), savedUser.getId());

        String token = jwtUtil.generateToken(savedUser.getEmail(), savedUser.getId());
        return AuthResponse.of(token, savedUser.getEmail(), savedUser.getFullName(), savedUser.getId());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            throw new BadCredentialsException("Invalid email or password");
        }

        AppUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        String passwordHash = user.getPasswordHash();
        if (passwordHash == null || !passwordEncoder.matches(request.getPassword(), passwordHash)) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        log.info("User logged in: {} (id={})", user.getEmail(), user.getId());
        return AuthResponse.of(token, user.getEmail(), user.getFullName(), user.getId());
    }
}

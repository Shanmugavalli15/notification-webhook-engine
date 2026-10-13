package com.example.notification.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String email;
    private String fullName;
    private Long userId;
    private String tokenType;

    public static AuthResponse of(String token, String email, String fullName, Long userId) {
        return AuthResponse.builder()
                .token(token)
                .email(email)
                .fullName(fullName)
                .userId(userId)
                .tokenType("Bearer")
                .build();
    }
}

package com.pocketpeers.backend.users.application.internal.outboundservices.tokens;

public interface TokenService {

    String generateToken(String username, String role, String fullName, String phoneNumber, String photo, String email);
    String getUsernameFromToken(String token);
    boolean validateToken(String token);
}
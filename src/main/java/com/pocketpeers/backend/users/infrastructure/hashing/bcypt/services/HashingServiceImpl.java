package com.pocketpeers.backend.users.infrastructure.hashing.bcypt.services;

import com.pocketpeers.backend.users.infrastructure.hashing.bcypt.BCryptHashingService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class HashingServiceImpl implements BCryptHashingService {
    private final BCryptPasswordEncoder passwordEncoder;

    HashingServiceImpl() {
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Hash a password using the BCrypt algorithm
     * @param rawPassword the password to hash
     * @return String the hashed password
     */
    @Override
    public String encode(CharSequence rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Check if a raw password matches a hashed password
     * @param rawPassword the raw password
     * @param encodedPassword the hashed password
     * @return boolean true if the raw password matches the hashed password, false otherwise
     */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

}

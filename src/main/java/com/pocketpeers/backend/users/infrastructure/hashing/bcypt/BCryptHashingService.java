package com.pocketpeers.backend.users.infrastructure.hashing.bcypt;

import com.pocketpeers.backend.users.application.internal.outboundservices.hashing.HashingService;
import org.springframework.security.crypto.password.PasswordEncoder;

public interface BCryptHashingService extends HashingService, PasswordEncoder {
}


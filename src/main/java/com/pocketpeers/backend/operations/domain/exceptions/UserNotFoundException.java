package com.pocketpeers.backend.operations.domain.exceptions;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long aLong) {
        super("User with id " + aLong + " not found");
    }
    public UserNotFoundException(String username) {
        super("User with username " + username + " not found");
    }
}

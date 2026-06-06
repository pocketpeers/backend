package com.pocketpeers.backend.groups.domain.exceptions;

public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(Long aLong) {
        super("Group with id " + aLong + " not found" );
    }
}

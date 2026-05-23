package com.pocketpeers.backend.users.interfaces.rest.resources;

public record AuthenticatedUserResource(Long id, String username, String token) {

}

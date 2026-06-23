package com.pocketpeers.backend.users.interfaces.rest.resources;

public record UserInformationResource(Long id,
                                      String username,
                                      String fullName,
                                      String phoneNumber,
                                      String photo,
                                      String email,
                                      Long userId) {
}

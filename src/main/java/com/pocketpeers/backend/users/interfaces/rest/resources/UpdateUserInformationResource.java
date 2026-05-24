package com.pocketpeers.backend.users.interfaces.rest.resources;

public record UpdateUserInformationResource(String firstName, String lastName, String phoneNumber, String photo, String email) {
}
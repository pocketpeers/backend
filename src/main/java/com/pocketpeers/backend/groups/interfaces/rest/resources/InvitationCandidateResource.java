package com.pocketpeers.backend.groups.interfaces.rest.resources;

/**
 * A quien se esta por invitar, para confirmarlo antes de mandar nada.
 *
 * @param availability AVAILABLE, ALREADY_MEMBER, ALREADY_INVITED o RECENTLY_REJECTED.
 */
public record InvitationCandidateResource(
        Long userId,
        String username,
        String fullName,
        String photo,
        String availability
) {
}

package com.pocketpeers.backend.groups.interfaces.rest.resources;

import java.util.Date;

public record GroupResource(
        Long id,
        String name,
       String description,
        String groupPhoto,
        Date createdAt,
        Date updatedAt,
        Long adminId
) {
}

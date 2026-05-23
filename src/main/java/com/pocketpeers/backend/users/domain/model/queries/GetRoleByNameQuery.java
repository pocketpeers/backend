package com.pocketpeers.backend.users.domain.model.queries;

import com.pocketpeers.backend.users.domain.model.valueobjects.Roles;

public record GetRoleByNameQuery(Roles name) {
}

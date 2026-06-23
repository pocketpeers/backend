package com.pocketpeers.backend.pbl.domain.services;

import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;

public interface PblCommandService {
    Long handle(RegisterReputationEventCommand command);
    void seedDefaultBadges();
}

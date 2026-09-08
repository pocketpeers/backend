package com.pocketpeers.backend.pbl.domain.services;

import com.pocketpeers.backend.pbl.domain.model.commands.RegisterReputationEventCommand;

public interface PblCommandService {
    Long handle(RegisterReputationEventCommand command);
    void seedDefaultBadges();

    /**
     * Reevalua las insignias de nivel de un usuario.
     *
     * <p>Existe porque el score de PeerScore cambia sin que ocurra ningun
     * evento: el peso de la evidencia decae con el tiempo, asi que el recalculo
     * nocturno puede hacer que alguien cruce un nivel sin haber hecho nada. Los
     * demas logros siguen colgando de eventos y no necesitan esto.</p>
     */
    void refreshLevelBadges(Long userId);
}

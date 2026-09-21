package com.pocketpeers.backend.pbl.domain.services;

import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.entities.BadgeCatalog;
import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.entities.UserBadge;
import com.pocketpeers.backend.pbl.domain.model.queries.GetGroupLeaderboardQuery;
import com.pocketpeers.backend.pbl.domain.model.queries.GetReputationHistoryQuery;
import com.pocketpeers.backend.pbl.domain.model.queries.GetUserReputationQuery;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.LeaderboardEntryResource;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.ReputationResource;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.ScoreSeriesPointResource;

import java.util.List;

public interface PblQueryService {
    UserReputation handle(GetUserReputationQuery query);

    /**
     * Vista completa de reputacion, con score, banda, desglose y objetivo.
     *
     * <p>Existe separada del agregado porque armarla implica recalcular, y eso
     * es una decision del servicio y no del controlador.</p>
     */
    ReputationResource getUserReputationResource(Long userId);
    List<ReputationEvent> handle(GetReputationHistoryQuery query);

    /**
     * Evolucion del score visible, reconstruida punto a punto.
     *
     * <p>Es la serie que dibuja el panel. Va aqui y no en el controlador por lo
     * mismo que {@link #getUserReputationResource}: implica recalcular, y ademas
     * decide que hacer cuando PeerScore esta apagado.</p>
     */
    List<ScoreSeriesPointResource> getScoreSeries(Long userId, int days, int points);
    List<UserBadge> getUserBadges(Long userId);
    List<BadgeCatalog> getAllBadges();
    List<LeaderboardEntryResource> handle(GetGroupLeaderboardQuery query);
}

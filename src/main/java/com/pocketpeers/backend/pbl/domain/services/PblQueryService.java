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
    List<UserBadge> getUserBadges(Long userId);
    List<BadgeCatalog> getAllBadges();
    List<LeaderboardEntryResource> handle(GetGroupLeaderboardQuery query);
}

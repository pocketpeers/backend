package com.pocketpeers.backend.pbl.interfaces.rest;

import com.pocketpeers.backend.pbl.domain.model.queries.GetGroupLeaderboardQuery;
import com.pocketpeers.backend.pbl.domain.model.queries.GetReputationHistoryQuery;
import com.pocketpeers.backend.pbl.domain.services.PblCommandService;
import com.pocketpeers.backend.pbl.domain.services.PblQueryService;
import com.pocketpeers.backend.pbl.interfaces.rest.resources.*;
import com.pocketpeers.backend.pbl.interfaces.rest.transform.RegisterReputationEventCommandFromResourceAssembler;
import com.pocketpeers.backend.pbl.interfaces.rest.transform.ReputationEventResourceFromEntityAssembler;
import com.pocketpeers.backend.shared.interfaces.rest.resources.MessageResource;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "api/v1/pbl", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "PBL Gamification", description = "Points, badges and leaderboard endpoints")
public class PblController {
    // REST facade for PBL. Controllers keep transport concerns here and defer
    // scoring, badge rules and leaderboard ordering to the domain services.
    private final PblCommandService pblCommandService;
    private final PblQueryService pblQueryService;
    private final UserInformationRepository userInformationRepository;
    private final UserRepository userRepository;

    public PblController(PblCommandService pblCommandService, PblQueryService pblQueryService,
                         UserInformationRepository userInformationRepository,
                         UserRepository userRepository) {
        this.pblCommandService = pblCommandService;
        this.pblQueryService = pblQueryService;
        this.userInformationRepository = userInformationRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/events")
    public ResponseEntity<MessageResource> registerReputationEvent(@RequestBody RegisterReputationEventResource resource) {
        var command = RegisterReputationEventCommandFromResourceAssembler.toCommandFromResource(resource);
        var eventId = pblCommandService.handle(command);
        return new ResponseEntity<>(new MessageResource("Registered reputation event with ID: " + eventId), HttpStatus.CREATED);
    }

    @PostMapping("/badges/seed")
    public ResponseEntity<MessageResource> seedBadges() {
        pblCommandService.seedDefaultBadges();
        return ResponseEntity.ok(new MessageResource("Default PBL badges seeded"));
    }

    @GetMapping("/users/{userId}/reputation")
    public ResponseEntity<ReputationResource> getUserReputation(@PathVariable Long userId) {
        return ResponseEntity.ok(pblQueryService.getUserReputationResource(userId));
    }

    @GetMapping("/users/{userId}/history")
    public ResponseEntity<List<ReputationEventResource>> getReputationHistory(@PathVariable Long userId,
                                                                              @RequestParam(defaultValue = "90") int days) {
        var history = pblQueryService.handle(new GetReputationHistoryQuery(userId, days)).stream()
                .map(ReputationEventResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(history);
    }

    /**
     * Evolucion del score, reconstruida en cortes de tiempo.
     *
     * <p>Separada de {@code /history}: ese devuelve los eventos, que son hechos
     * registrados, y esta devuelve el score que esos hechos producian en cada
     * momento. Mezclarlos en una sola respuesta ataria el numero de puntos de la
     * grafica a cuantos eventos hubo.</p>
     */
    @GetMapping("/users/{userId}/score-series")
    public ResponseEntity<List<ScoreSeriesPointResource>> getScoreSeries(@PathVariable Long userId,
                                                                         @RequestParam(defaultValue = "90") int days,
                                                                         @RequestParam(defaultValue = "12") int points) {
        return ResponseEntity.ok(pblQueryService.getScoreSeries(userId, days, points));
    }

    @GetMapping("/users/{userId}/badges")
    public ResponseEntity<List<BadgeResource>> getUserBadges(@PathVariable Long userId) {
        return ResponseEntity.ok(badgesForUser(userId));
    }

    @GetMapping("/groups/{groupId}/leaderboard")
    public ResponseEntity<List<LeaderboardEntryResource>> getGroupLeaderboard(@PathVariable Long groupId,
                                                                              @RequestParam Long viewerUserId) {
        return ResponseEntity.ok(pblQueryService.handle(new GetGroupLeaderboardQuery(groupId, viewerUserId)));
    }

    @GetMapping("/groups/{groupId}/members/{memberId}/public-profile")
    public ResponseEntity<PublicMemberProfileResource> getPublicMemberProfile(@PathVariable Long groupId,
                                                                              @PathVariable Long memberId) {
        // Public profile combines user information with reputation and unlocked
        // badges so the mobile app does not need multiple round trips.
        var reputation = pblQueryService.getUserReputationResource(memberId);
        var userInfo = userInformationRepository.findByUserId(memberId);
        var profile = new PublicMemberProfileResource(
                memberId,
                userInfo.map(UserInformation::getFullName).orElse(""),
                // El usuario, no el correo: el correo nunca sale en perfiles ajenos.
                userRepository.findById(memberId).map(User::getUsername).orElse(""),
                userInfo.map(UserInformation::getPhoto).orElse(""),
                reputation,
                badgesForUser(memberId).stream().filter(BadgeResource::unlocked).toList(),
                reputation.completedPayments()
        );
        return ResponseEntity.ok(profile);
    }

    private List<BadgeResource> badgesForUser(Long userId) {
        // Return the full badge catalog and mark which ones are unlocked. This
        // lets the UI show locked and unlocked achievements in one response.
        var unlocked = pblQueryService.getUserBadges(userId);
        return pblQueryService.getAllBadges().stream()
                .map(badge -> unlocked.stream()
                        .filter(userBadge -> userBadge.getBadge().getCode().equals(badge.getCode()))
                        .findFirst()
                        .map(userBadge -> new BadgeResource(badge.getId(), badge.getCode(), badge.getName(),
                                badge.getDescription(), true, userBadge.getUnlockedAt()))
                        .orElseGet(() -> new BadgeResource(badge.getId(), badge.getCode(), badge.getName(),
                                badge.getDescription(), false, null)))
                .toList();
    }
}

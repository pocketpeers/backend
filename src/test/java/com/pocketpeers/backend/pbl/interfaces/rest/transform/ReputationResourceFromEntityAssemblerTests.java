package com.pocketpeers.backend.pbl.interfaces.rest.transform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.NextLevelGoal;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationLevel;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ScoreResult;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * De donde salen el score y el nivel que ve el usuario.
 *
 * <p>Es el punto donde el interruptor de configuracion se vuelve visible, y por
 * eso importa que las dos posiciones esten cubiertas: apagarlo tiene que
 * devolver exactamente lo que la app mostraba antes.</p>
 */
class ReputationResourceFromEntityAssemblerTests {

    @Test
    void servesPeerScoreWhenTheEngineIsOn() {
        var reputation = reputation(4, 12);
        var result = new ScoreResult(65.9, 40.3, 87.7, ReputationLevel.BRONZE, 2.98, 3, 4.5, 2.3,
                List.of(new ScoreResult.Contribution("Cumplimiento reciente", 8.5)));
        var goal = new NextLevelGoal(ReputationLevel.SILVER, 0.0, 0.02, true);

        var resource = ReputationResourceFromEntityAssembler.toResourceFromEntity(
                reputation, result, goal, true);

        assertThat(resource.peerScoreActive()).isTrue();
        assertThat(resource.peerScore()).isEqualTo(65.9);
        // El campo entero se conserva por compatibilidad y redondea: truncar le
        // restaria casi un punto entero a casi todo el mundo.
        assertThat(resource.score()).isEqualTo(66);
        assertThat(resource.level()).isEqualTo(ReputationLevel.BRONZE.getDisplayName());
        assertThat(resource.band().low()).isEqualTo(40.3);
        assertThat(resource.band().high()).isEqualTo(87.7);
        assertThat(resource.effectiveCounterparties()).isEqualTo(2.98);
        assertThat(resource.distinctCounterparties()).isEqualTo(3);
        assertThat(resource.breakdown()).hasSize(1);
        assertThat(resource.nextLevel().level()).isEqualTo("SILVER");
        assertThat(resource.nextLevel().bandTooWide()).isTrue();

        // Los contadores de conducta no los toca PeerScore: siguen alimentando
        // las insignias de racha.
        assertThat(resource.onTimePaymentStreak()).isEqualTo(4);
        assertThat(resource.completedPayments()).isEqualTo(12);
    }

    @Test
    void fallsBackToThePointCounterWhenTheEngineIsOff() {
        var reputation = reputation(4, 12);
        ReflectionTestUtils.setField(reputation, "score", 70);
        var result = new ScoreResult(20.0, 5.0, 40.0, ReputationLevel.NEW, 1.0, 1, 1.0, 3.0, List.of());

        var resource = ReputationResourceFromEntityAssembler.toResourceFromEntity(
                reputation, result, null, false);

        assertThat(resource.peerScoreActive()).isFalse();
        assertThat(resource.score()).isEqualTo(70);
        assertThat(resource.level()).isEqualTo(ReputationLevel.SILVER.getDisplayName());
        assertThat(resource.pointsToNextLevel()).isEqualTo(15);

        // El calculo se sigue publicando aunque no gobierne lo visible: es lo que
        // permite comparar los dos motores sobre datos reales antes de activarlo.
        assertThat(resource.peerScore()).isEqualTo(20.0);
        assertThat(resource.band()).isNotNull();
    }

    @Test
    void roundsTheRemainingPointsUpSoAlmostThereIsNotShownAsArrived() {
        var result = new ScoreResult(59.8, 50.0, 66.0, ReputationLevel.BRONZE, 3.0, 3, 4.0, 2.0, List.of());
        var goal = new NextLevelGoal(ReputationLevel.SILVER, 0.2, 0.0, false);

        var resource = ReputationResourceFromEntityAssembler.toResourceFromEntity(
                reputation(0, 0), result, goal, true);

        assertThat(resource.pointsToNextLevel()).isEqualTo(1);
    }

    @Test
    void omitsTheGoalAtTheTopLevel() {
        var result = new ScoreResult(95.0, 90.0, 99.0, ReputationLevel.GOLD, 5.0, 5, 20.0, 1.0, List.of());

        var resource = ReputationResourceFromEntityAssembler.toResourceFromEntity(
                reputation(0, 0), result, null, true);

        assertThat(resource.nextLevel()).isNull();
        assertThat(resource.pointsToNextLevel()).isZero();
    }

    private UserReputation reputation(int streak, int completedPayments) {
        var user = new User("ana", "secret");
        ReflectionTestUtils.setField(user, "id", 11L);
        var reputation = new UserReputation(user);
        ReflectionTestUtils.setField(reputation, "onTimePaymentStreak", streak);
        ReflectionTestUtils.setField(reputation, "completedPayments", completedPayments);
        return reputation;
    }
}

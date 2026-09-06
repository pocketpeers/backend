package com.pocketpeers.backend.pbl.domain.model.valueobjects;

import java.util.List;

/**
 * Salida de PeerScore.
 *
 * <p>El score nunca viaja solo. La banda dice cuanta confianza merece ese numero,
 * y el desglose dice de donde salio. Un numero sin explicacion comunica un
 * veredicto; un numero explicado ensena la relacion entre lo que la persona hizo
 * y lo que le paso, que es el objetivo del producto.</p>
 *
 * @param score            estimacion puntual en [0, 100]
 * @param bandLow          percentil 5 de la estimacion
 * @param bandHigh         percentil 95 de la estimacion
 * @param level            nivel alcanzado segun puntaje, certeza y diversidad
 * @param effectiveCounterparties contrapartes efectivas (inverso del indice de concentracion)
 * @param distinctCounterparties  contrapartes distintas, sin ponderar
 * @param alpha            parametro alpha del posterior, se persiste para auditar
 * @param beta             parametro beta del posterior, se persiste para auditar
 * @param breakdown        aporte de cada bloque de eventos al score
 */
public record ScoreResult(
        double score,
        double bandLow,
        double bandHigh,
        ReputationLevel level,
        double effectiveCounterparties,
        int distinctCounterparties,
        double alpha,
        double beta,
        List<Contribution> breakdown
) {

    /** Ancho de la banda: es lo que mide cuanto sabe el sistema sobre esta persona. */
    public double bandWidth() {
        return bandHigh - bandLow;
    }

    /**
     * Aporte marginal de un bloque de eventos.
     *
     * <p>El delta se calcula por exclusion: cuanto cambiaria el score si ese
     * bloque no existiera. Es atribucion marginal, no una suma de puntos, asi que
     * los deltas no tienen por que sumar el score total.</p>
     *
     * @param label descripcion legible por una persona, no por un desarrollador
     * @param delta cuanto aporta o resta ese bloque, en puntos de score
     */
    public record Contribution(String label, double delta) {
    }
}

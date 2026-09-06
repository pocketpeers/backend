package com.pocketpeers.backend.pbl.domain.model.valueobjects;

/**
 * Parametros de calibracion de PeerScore.
 *
 * <p>Se pasan como argumento en vez de estar fijos en el codigo porque los
 * experimentos de validacion necesitan barrer estos valores para medir su
 * efecto. Cambiar un valor aqui cambia el comportamiento del score sin tocar
 * el algoritmo.</p>
 *
 * @param priorStrength          peso del promedio del grupo en un usuario sin historial,
 *                               expresado en cantidad equivalente de observaciones
 * @param counterpartyCap        evidencia maxima que puede aportar una sola contraparte
 * @param halfLifeDays           dias tras los cuales un evento pesa la mitad
 * @param maxEventWeight         tope al peso por monto, para que un gasto atipico no domine
 * @param reciprocityThreshold   desde que nivel de reciprocidad se empieza a descontar
 * @param reciprocityPenalty     descuento maximo aplicado a una contraparte reciproca
 * @param bronzeScore            puntaje minimo para Bronce
 * @param silverScore            puntaje minimo para Plata
 * @param goldScore              puntaje minimo para Oro
 * @param silverMaxBandWidth     ancho de banda maximo tolerado para Plata
 * @param goldMaxBandWidth       ancho de banda maximo tolerado para Oro
 * @param bronzeMinCounterparties contrapartes efectivas minimas para Bronce
 * @param silverMinCounterparties contrapartes efectivas minimas para Plata
 * @param goldMinCounterparties   contrapartes efectivas minimas para Oro
 */
public record ScoreParameters(
        double priorStrength,
        double counterpartyCap,
        double halfLifeDays,
        double maxEventWeight,
        double reciprocityThreshold,
        double reciprocityPenalty,
        double bronzeScore,
        double silverScore,
        double goldScore,
        double silverMaxBandWidth,
        double goldMaxBandWidth,
        double bronzeMinCounterparties,
        double silverMinCounterparties,
        double goldMinCounterparties
) {

    /** Numero minimo de eventos para que las estadisticas de un grupo se consideren confiables. */
    public static final int MIN_GROUP_EVENTS = 20;

    /**
     * Valores por defecto.
     *
     * <p>Los umbrales de puntaje 25/60/85 se conservan del motor PBL vigente para
     * que la interfaz movil y el catalogo de insignias no cambien.</p>
     */
    public static ScoreParameters defaults() {
        return new ScoreParameters(
                4.0,    // priorStrength
                0.35,   // counterpartyCap
                90.0,   // halfLifeDays
                3.0,    // maxEventWeight
                0.80,   // reciprocityThreshold
                0.50,   // reciprocityPenalty
                25.0,   // bronzeScore
                60.0,   // silverScore
                85.0,   // goldScore
                18.0,   // silverMaxBandWidth
                12.0,   // goldMaxBandWidth
                2.0,    // bronzeMinCounterparties
                3.0,    // silverMinCounterparties
                4.0     // goldMinCounterparties
        );
    }

    public ScoreParameters {
        if (priorStrength <= 0) {
            throw new IllegalArgumentException("priorStrength debe ser positivo");
        }
        if (counterpartyCap <= 0 || counterpartyCap > 1) {
            throw new IllegalArgumentException("counterpartyCap debe estar en (0, 1]");
        }
        if (halfLifeDays <= 0) {
            throw new IllegalArgumentException("halfLifeDays debe ser positivo");
        }
        if (maxEventWeight < 1) {
            throw new IllegalArgumentException("maxEventWeight no puede ser menor que 1");
        }
        if (reciprocityThreshold < 0 || reciprocityThreshold >= 1) {
            throw new IllegalArgumentException("reciprocityThreshold debe estar en [0, 1)");
        }
        if (reciprocityPenalty < 0 || reciprocityPenalty > 1) {
            throw new IllegalArgumentException("reciprocityPenalty debe estar en [0, 1]");
        }
    }
}

package com.pocketpeers.backend.pbl.domain.services;

import org.apache.commons.math3.distribution.BetaDistribution;

/**
 * Cuantiles de la distribucion Beta, acotados para que nunca fallen.
 *
 * <p>La banda de confianza de PeerScore son los percentiles 5 y 95 del posterior
 * Beta(alpha, beta). La libreria resuelve el calculo, pero se rompe en los
 * extremos: con parametros muy chicos la distribucion es degenerada y con
 * parametros enormes la inversion numerica se vuelve costosa o no converge.</p>
 *
 * <p>Un usuario real nunca llega a esos extremos, pero las cohortes sinteticas de
 * los experimentos si: simular cien mil pagos puntuales produce un alpha que
 * desborda el calculo. Acotar aqui evita que un experimento se caiga a la mitad.</p>
 */
public final class BetaQuantiles {

    private static final double MIN_PARAM = 0.01;
    private static final double MAX_PARAM = 1e6;
    private static final double LOW_PERCENTILE = 0.05;
    private static final double HIGH_PERCENTILE = 0.95;

    private BetaQuantiles() {
    }

    /**
     * Devuelve {percentil 5, percentil 95} escalados a [0, 100].
     */
    public static double[] band(double alpha, double beta) {
        double a = clamp(alpha);
        double b = clamp(beta);

        try {
            BetaDistribution distribution = new BetaDistribution(a, b);
            double low = distribution.inverseCumulativeProbability(LOW_PERCENTILE);
            double high = distribution.inverseCumulativeProbability(HIGH_PERCENTILE);
            return new double[]{low * 100.0, high * 100.0};
        } catch (RuntimeException e) {
            // Si la inversion numerica no converge, se cae a una aproximacion normal.
            // Es menos precisa, pero un score sin banda no se puede mostrar y una
            // excepcion aqui tumbaria el recalculo nocturno de todos los usuarios.
            return normalApproximation(a, b);
        }
    }

    /**
     * Aproximacion normal a la banda, usada solo como respaldo.
     *
     * <p>Con alpha y beta grandes la Beta se parece a una normal, que es
     * justamente el caso donde la inversion exacta da problemas.</p>
     */
    private static double[] normalApproximation(double a, double b) {
        double total = a + b;
        double mean = a / total;
        double variance = (a * b) / (total * total * (total + 1.0));
        double sd = Math.sqrt(variance);
        double z = 1.6448536269514722; // percentil 95 de la normal estandar
        double low = Math.max(0.0, mean - z * sd);
        double high = Math.min(1.0, mean + z * sd);
        return new double[]{low * 100.0, high * 100.0};
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return MIN_PARAM;
        }
        return Math.max(MIN_PARAM, Math.min(MAX_PARAM, value));
    }
}

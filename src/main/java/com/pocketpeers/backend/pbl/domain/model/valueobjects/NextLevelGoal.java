package com.pocketpeers.backend.pbl.domain.model.valueobjects;

/**
 * Lo que le falta a un usuario para el siguiente nivel.
 *
 * <p>Existe porque el nivel de PeerScore exige tres condiciones a la vez, y un
 * usuario al que solo le falta una no puede deducir cual mirando el score. Sin
 * este desglose la interfaz solo puede mostrar un numero; con el puede decir
 * "te falta cerrar tu banda" o "te falta una contraparte mas", que es lo que
 * convierte el score en algo que se puede accionar.</p>
 *
 * @param level                 nivel al que se aspira
 * @param missingScore          puntos que faltan, 0 si el puntaje ya alcanza
 * @param missingCounterparties contrapartes efectivas que faltan, 0 si ya alcanzan
 * @param bandTooWide           si la banda es todavia demasiado ancha para ese nivel
 */
public record NextLevelGoal(
        ReputationLevel level,
        double missingScore,
        double missingCounterparties,
        boolean bandTooWide
) {
}

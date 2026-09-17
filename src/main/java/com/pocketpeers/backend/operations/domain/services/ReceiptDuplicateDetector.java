package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.valueobjects.ReceiptPerceptualHash;
import com.pocketpeers.backend.shared.domain.services.ImageFingerprinter;

import java.util.List;
import java.util.Optional;

/**
 * Parte decidible del control de duplicados: dada una huella perceptual y las ya
 * registradas, dice cual se le parece.
 *
 * <p>Funcion pura, sin Spring ni repositorios, por el mismo motivo que
 * {@code PeerScoreCalculator}: el umbral hay que poder barrerlo sobre imagenes
 * sinteticas para justificarlo, y eso no se puede hacer si la logica necesita
 * una base de datos.</p>
 *
 * <p><b>Lo que esta clase NO hace: rechazar.</b> Ver
 * {@link #SUSPICION_HAMMING_DISTANCE} para el porque.</p>
 */
public final class ReceiptDuplicateDetector {

    /**
     * Distancia por debajo de la cual dos comprobantes se marcan como
     * sospechosos de ser el mismo.
     *
     * <p><b>Es un aviso, no un rechazo, y la medicion es la razon.</b> Sobre
     * doce documentos sinteticos distintos, las distancias entre pares fueron
     * min 2, max 8, media 5.45; la misma imagen reenviada con reescalado a la
     * mitad y recompresion JPEG al 40% llego a 3. Las dos poblaciones se
     * solapan: no hay corte que acepte todo reenvio sin rechazar tambien
     * documentos genuinamente distintos.</p>
     *
     * <p>Agrandar el hash no lo arregla, porque escala las dos distancias a la
     * vez: con 256 bits el solape fue 6 contra 7, y con 576 bits, 13 contra 18.
     * La causa es estructural — los documentos son casi todos texto oscuro sobre
     * fondo claro, asi que sus mosaicos reducidos se parecen entre si.</p>
     *
     * <p>De ahi la decision: el bloqueo duro lo sostienen el SHA-256 y la llave
     * RUC + numero, que son igualdades y no admiten falso positivo. Esta senal
     * queda para senalar el caso a una persona, que es lo unico defendible con
     * una medida que se solapa.</p>
     */
    public static final int SUSPICION_HAMMING_DISTANCE = 3;

    private ReceiptDuplicateDetector() {
    }

    public static Optional<ReceiptPerceptualHash> findSuspectedDuplicate(
            long candidate,
            List<ReceiptPerceptualHash> registered) {
        return findSuspectedDuplicate(candidate, registered, SUSPICION_HAMMING_DISTANCE);
    }

    /**
     * El mas parecido por debajo del umbral, o vacio si ninguno lo esta.
     *
     * <p>Devuelve el minimo y no el primero que cumple para que el aviso senale
     * el conflicto real cuando hay varios candidatos, y para que el resultado no
     * dependa del orden en que la consulta devolvio las filas.</p>
     */
    public static Optional<ReceiptPerceptualHash> findSuspectedDuplicate(
            long candidate,
            List<ReceiptPerceptualHash> registered,
            int maxDistance) {
        ReceiptPerceptualHash closest = null;
        int closestDistance = Integer.MAX_VALUE;

        for (ReceiptPerceptualHash existing : registered) {
            if (existing.perceptualHash() == null) {
                continue;
            }
            int distance = ImageFingerprinter.distance(candidate, existing.perceptualHash());
            if (distance <= maxDistance && distance < closestDistance) {
                closest = existing;
                closestDistance = distance;
            }
        }

        return Optional.ofNullable(closest);
    }
}

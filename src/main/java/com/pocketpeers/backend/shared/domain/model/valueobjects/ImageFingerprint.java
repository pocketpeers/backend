package com.pocketpeers.backend.shared.domain.model.valueobjects;

/**
 * Huella de una imagen, en dos señales que atrapan cosas distintas.
 *
 * <p>{@code sha256} identifica el archivo: cambia con cualquier bit. Detecta el
 * reenvío del mismo archivo y nada más.</p>
 *
 * <p>{@code perceptualHash} identifica lo que se ve: sobrevive al reescalado, a
 * la recompresión y a recortes menores. Detecta la misma captura reenviada en
 * otro formato, que es la evasión barata contra la que el SHA-256 no sirve.</p>
 *
 * <p>Ninguna de las dos detecta una segunda fotografía del mismo papel: esa
 * produce píxeles genuinamente distintos y solo la delata el número de
 * comprobante. Por eso el control de duplicados usa además la llave lógica
 * RUC + serie-número, y por eso ninguna de las tres sobra.</p>
 *
 * @param sha256 64 caracteres hexadecimales, o {@code null} si no se calculó
 * @param perceptualHash dHash de 64 bits, o {@code null} si la imagen no se
 *                       pudo decodificar (archivo corrupto o no-imagen)
 */
public record ImageFingerprint(String sha256, Long perceptualHash) {

    public static ImageFingerprint none() {
        return new ImageFingerprint(null, null);
    }

    public boolean hasSha256() {
        return sha256 != null && !sha256.isBlank();
    }

    public boolean hasPerceptualHash() {
        return perceptualHash != null;
    }
}

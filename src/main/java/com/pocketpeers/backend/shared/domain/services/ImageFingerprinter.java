package com.pocketpeers.backend.shared.domain.services;

import com.pocketpeers.backend.shared.domain.model.valueobjects.ImageFingerprint;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Calcula la huella de una imagen. Función pura: sin Spring, sin repositorios,
 * sin reloj — igual que {@code PeerScoreCalculator}, y por el mismo motivo: así
 * se puede probar el algoritmo sin levantar la aplicación.
 */
public final class ImageFingerprinter {

    /** El dHash compara píxeles contra su vecino derecho, así que necesita una columna extra. */
    private static final int HASH_WIDTH = 9;
    private static final int HASH_HEIGHT = 8;

    private ImageFingerprinter() {
    }

    /**
     * Huella completa. Se le pasan los bytes tal como se van a almacenar, no los
     * que subió el cliente: la canonicalización de {@code ImageServiceImpl}
     * (reescalado a 1600 px y recodificación a JPEG) descarta los metadatos
     * EXIF, así que dos archivos idénticos en píxeles pero distintos en EXIF
     * caen en el mismo SHA-256. Hashear los bytes originales dejaría pasar esa
     * evasión, que cuesta un clic en cualquier editor.
     *
     * @param storedData    bytes que quedan guardados
     * @param decodedImage  la imagen ya decodificada, o {@code null} si no se pudo
     */
    public static ImageFingerprint fingerprint(byte[] storedData, BufferedImage decodedImage) {
        return new ImageFingerprint(
                storedData == null ? null : sha256(storedData),
                decodedImage == null ? null : perceptualHash(decodedImage)
        );
    }

    public static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno esta roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    /**
     * dHash de 64 bits.
     *
     * <p>Reduce la imagen a 9x8 en escala de gris y emite un bit por cada par de
     * píxeles vecinos: 1 si el izquierdo es más claro. Ocho filas por ocho
     * comparaciones dan exactamente 64 bits.</p>
     *
     * <p>Se eligió dHash sobre pHash porque no necesita transformada de coseno
     * —y por tanto ninguna dependencia nueva— y porque sobre documentos, que son
     * de alto contraste, el gradiente horizontal discrimina bien. El reescalado
     * a 9x8 es lo que le da la tolerancia: dos codificaciones JPEG distintas de
     * la misma foto colapsan al mismo mosaico.</p>
     */
    public static long perceptualHash(BufferedImage image) {
        BufferedImage thumbnail = new BufferedImage(HASH_WIDTH, HASH_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, HASH_WIDTH, HASH_HEIGHT, null);
        } finally {
            graphics.dispose();
        }

        long hash = 0L;
        for (int y = 0; y < HASH_HEIGHT; y++) {
            int left = luminance(thumbnail.getRGB(0, y));
            for (int x = 1; x < HASH_WIDTH; x++) {
                int right = luminance(thumbnail.getRGB(x, y));
                hash = (hash << 1) | (left > right ? 1L : 0L);
                left = right;
            }
        }
        return hash;
    }

    /** Bits en que difieren dos huellas. 0 es idéntico; 64, opuesto. */
    public static int distance(long left, long right) {
        return Long.bitCount(left ^ right);
    }

    /** Luminancia ITU-R BT.601, la misma ponderación que usa JPEG para el canal Y. */
    private static int luminance(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }
}

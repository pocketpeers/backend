package com.pocketpeers.backend.groups.application.internal.declarations;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * La firma dibujada en la aplicacion, recibida como PNG en base64.
 *
 * <p>Se valida aqui y no solo en la aplicacion: el endpoint lo puede llamar
 * cualquiera, y un PDF con una "firma" vacia o que no es una imagen no firma
 * nada.</p>
 */
public final class SignatureImage {

    private static final int MAX_BYTES = 300 * 1024;
    private static final int MAX_SIDE = 2000;
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** Pixeles con trazo que hacen falta para no considerar la firma en blanco. */
    private static final int MIN_INK_PIXELS = 50;

    private SignatureImage() {
    }

    public static BufferedImage decode(String base64Png) {
        if (base64Png == null || base64Png.isBlank()) {
            throw new IllegalArgumentException("Debes dibujar tu firma para aceptar la declaracion jurada");
        }

        var encoded = base64Png.strip();
        var comma = encoded.indexOf(',');
        if (encoded.startsWith("data:") && comma > 0) {
            encoded = encoded.substring(comma + 1);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La firma no tiene un formato valido");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("La imagen de la firma es demasiado grande");
        }
        if (!startsWithPngMagic(bytes)) {
            throw new IllegalArgumentException("La firma debe enviarse como imagen PNG");
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            image = null;
        }
        if (image == null || image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE) {
            throw new IllegalArgumentException("La firma no tiene un formato valido");
        }
        if (inkPixels(image) < MIN_INK_PIXELS) {
            throw new IllegalArgumentException("La firma esta en blanco; dibujala de nuevo");
        }
        return image;
    }

    private static boolean startsWithPngMagic(byte[] bytes) {
        if (bytes.length < PNG_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (bytes[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** Cuenta pixeles opacos y oscuros: el trazo, sea el fondo blanco o transparente. */
    private static int inkPixels(BufferedImage image) {
        var count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                var argb = image.getRGB(x, y);
                var alpha = argb >>> 24;
                var luminance = (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3;
                if (alpha > 64 && luminance < 160 && ++count >= MIN_INK_PIXELS) {
                    return count;
                }
            }
        }
        return count;
    }
}

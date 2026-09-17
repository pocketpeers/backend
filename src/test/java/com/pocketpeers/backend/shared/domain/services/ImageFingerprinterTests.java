package com.pocketpeers.backend.shared.domain.services;

import com.pocketpeers.backend.operations.domain.services.ReceiptDuplicateDetector;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFingerprinterTests {

    @Test
    void sha256EsDeterministaYDistingueBytes() {
        byte[] data = "boleta".getBytes();

        assertThat(ImageFingerprinter.sha256(data)).isEqualTo(ImageFingerprinter.sha256(data));
        assertThat(ImageFingerprinter.sha256(data)).hasSize(64);
        assertThat(ImageFingerprinter.sha256(data))
                .isNotEqualTo(ImageFingerprinter.sha256("boletas".getBytes()));
    }

    @Test
    void laMismaImagenDaLaMismaHuellaPerceptual() {
        assertThat(ImageFingerprinter.perceptualHash(syntheticReceipt(600, 800, 7)))
                .isEqualTo(ImageFingerprinter.perceptualHash(syntheticReceipt(600, 800, 7)));
    }

    /**
     * Lo que justifica tener huella perceptual ademas del SHA-256: reenviar la
     * captura por un canal que la recomprime y la reescala cambia todos los
     * bytes, pero casi nada de lo que se ve.
     */
    @Test
    void sobreviveALaRecompresionYAlReescalado() throws Exception {
        BufferedImage original = syntheticReceipt(600, 800, 7);
        BufferedImage reenviada = decode(toJpeg(resize(original, 300, 400), 0.4f));

        assertThat(ImageFingerprinter.distance(
                ImageFingerprinter.perceptualHash(original),
                ImageFingerprinter.perceptualHash(reenviada)))
                .isLessThanOrEqualTo(ReceiptDuplicateDetector.SUSPICION_HAMMING_DISTANCE);
    }

    /**
     * La razon por la que el parecido visual avisa en vez de rechazar.
     *
     * <p>Mide las dos poblaciones que un umbral tendria que separar: la
     * distancia entre documentos genuinamente distintos y la que deja un reenvio
     * recomprimido del mismo documento. <b>Se solapan</b>, asi que no existe
     * corte que atrape todo reenvio sin rechazar tambien boletas legitimas: los
     * documentos son casi todos texto oscuro sobre fondo claro y sus mosaicos
     * reducidos se parecen entre si.</p>
     *
     * <p>Esta prueba existe para que el solape quede registrado. Si alguien mas
     * adelante convierte la sospecha en rechazo, esta clase es donde deberia
     * mirar antes.</p>
     */
    @Test
    void laDistanciaPerceptualNoSeparaDocumentosDistintosDeReenvios() throws Exception {
        int cohorte = 12;
        long[] huellas = new long[cohorte];
        for (int i = 0; i < cohorte; i++) {
            huellas[i] = ImageFingerprinter.perceptualHash(syntheticReceipt(600, 800, i + 1));
        }

        int minEntreDistintos = Integer.MAX_VALUE;
        for (int i = 0; i < cohorte; i++) {
            for (int j = i + 1; j < cohorte; j++) {
                minEntreDistintos = Math.min(minEntreDistintos,
                        ImageFingerprinter.distance(huellas[i], huellas[j]));
            }
        }

        int maxEntreReenvios = 0;
        for (int i = 0; i < cohorte; i++) {
            BufferedImage original = syntheticReceipt(600, 800, i + 1);
            BufferedImage reenviada = decode(toJpeg(resize(original, 300, 400), 0.4f));
            maxEntreReenvios = Math.max(maxEntreReenvios, ImageFingerprinter.distance(
                    ImageFingerprinter.perceptualHash(original),
                    ImageFingerprinter.perceptualHash(reenviada)));
        }

        assertThat(maxEntreReenvios)
                .as("las poblaciones se solapan: por eso el parecido visual avisa y no rechaza")
                .isGreaterThanOrEqualTo(minEntreDistintos);
    }

    @Test
    void huellaSinImagenDecodificableConservaElSha256() {
        var fingerprint = ImageFingerprinter.fingerprint("no es una imagen".getBytes(), null);

        assertThat(fingerprint.hasSha256()).isTrue();
        assertThat(fingerprint.hasPerceptualHash()).isFalse();
    }

    @Test
    void distanciaEsCeroContraSiMismaYSesentaYCuatroContraElOpuesto() {
        assertThat(ImageFingerprinter.distance(0xCAFEBABEL, 0xCAFEBABEL)).isZero();
        assertThat(ImageFingerprinter.distance(0L, -1L)).isEqualTo(64);
    }

    /**
     * Documento sintetico con renglones de sangria y largo variables, que es lo
     * que distingue una boleta de otra del mismo comercio. Una grilla regular
     * daria el mismo mosaico de 9x8 para todas y las mediciones no dirian nada.
     */
    private static BufferedImage syntheticReceipt(int width, int height, int seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            Random random = new Random(seed);
            for (int row = 0; row < 16; row++) {
                int indent = 20 + random.nextInt(width / 3);
                int lineWidth = 30 + random.nextInt(width - indent - 20);
                g.fillRect(indent, 30 + row * (height / 18), lineWidth, height / 40);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] toJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] data) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(data));
    }
}

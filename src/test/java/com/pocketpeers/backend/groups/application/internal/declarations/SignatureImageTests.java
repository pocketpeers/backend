package com.pocketpeers.backend.groups.application.internal.declarations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

class SignatureImageTests {

    @Test
    void drawnSignatureIsAccepted() throws IOException {
        var image = SignatureImage.decode(base64Png(signature()));

        assertThat(image.getWidth()).isEqualTo(600);
    }

    @Test
    void dataUriPrefixIsAccepted() throws IOException {
        assertThat(SignatureImage.decode("data:image/png;base64," + base64Png(signature()))).isNotNull();
    }

    @Test
    void blankSignatureIsRejected() throws IOException {
        var blank = new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB);

        assertThatThrownBy(() -> SignatureImage.decode(base64Png(blank)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en blanco");
    }

    @Test
    void missingSignatureIsRejected() {
        assertThatThrownBy(() -> SignatureImage.decode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dibujar tu firma");
    }

    @Test
    void nonPngIsRejected() {
        var notPng = Base64.getEncoder().encodeToString("esto no es una imagen".getBytes());

        assertThatThrownBy(() -> SignatureImage.decode(notPng))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PNG");
    }

    static BufferedImage signature() {
        var image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4));
        g.drawLine(40, 150, 200, 40);
        g.drawLine(200, 40, 320, 160);
        g.drawLine(320, 160, 560, 60);
        g.dispose();
        return image;
    }

    static String base64Png(BufferedImage image) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}

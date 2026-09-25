package com.pocketpeers.backend.groups.application.internal.declarations;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Arma el PDF de la declaracion firmada: el texto, la firma dibujada debajo y
 * los datos de quien firma.
 *
 * <p>Usa las fuentes estandar de PDF (Helvetica), que no hay que incrustar y
 * cubren el castellano. Lo que no entre en esa codificacion —un emoji en un
 * nombre, por ejemplo— se reemplaza por "?" en vez de hacer fallar la firma.</p>
 */
public class MembershipDeclarationPdfRenderer {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final float MARGIN = 50;
    private static final float BODY_SIZE = 10;
    private static final float TITLE_SIZE = 11;
    private static final float LEADING = 1.35f;
    private static final float SIGNATURE_MAX_WIDTH = 200;
    private static final float SIGNATURE_MAX_HEIGHT = 80;

    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public record SignatureBlock(String fullName, String documentLabel, String groupName,
                                 String declarationVersion, Instant acceptedAt, String textHash) {
    }

    public byte[] render(String declarationText, BufferedImage signature, SignatureBlock block) {
        try (var document = new PDDocument()) {
            var writer = new PageWriter(document);

            var lines = declarationText.strip().split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                var line = lines[i].strip();
                if (line.isEmpty()) {
                    writer.skip(BODY_SIZE * 0.5f);
                } else if (i == 0) {
                    writer.paragraph(line, bold, TITLE_SIZE);
                } else if (line.matches("^\\d+\\. .*") && line.equals(line.toUpperCase())) {
                    writer.paragraph(line, bold, BODY_SIZE);
                } else {
                    writer.paragraph(line, regular, BODY_SIZE);
                }
            }

            writeSignature(document, writer, signature, block);
            writer.close();
            writeFooters(document, block.declarationVersion());

            var info = new PDDocumentInformation();
            info.setTitle("Declaración jurada - " + sanitize(block.groupName()));
            info.setAuthor(sanitize(block.fullName()));
            info.setCreator("PocketPeers");
            var created = new GregorianCalendar(TimeZone.getTimeZone(LIMA));
            created.setTimeInMillis(block.acceptedAt().toEpochMilli());
            info.setCreationDate(created);
            document.setDocumentInformation(info);

            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la declaracion", e);
        }
    }

    private void writeSignature(PDDocument document, PageWriter writer, BufferedImage signature,
                                SignatureBlock block) throws IOException {
        var scale = Math.min(SIGNATURE_MAX_WIDTH / signature.getWidth(),
                SIGNATURE_MAX_HEIGHT / signature.getHeight());
        var width = signature.getWidth() * scale;
        var height = signature.getHeight() * scale;

        // Firma, linea y datos van juntos: si no caben, pasan enteros a la pagina siguiente.
        writer.ensureSpace(height + 110);
        writer.skip(BODY_SIZE * 2);

        var image = LosslessFactory.createFromImage(document, signature);
        var top = writer.y;
        writer.stream.drawImage(image, MARGIN, top - height, width, height);
        writer.y = top - height - 4;

        writer.stream.setLineWidth(0.7f);
        writer.stream.moveTo(MARGIN, writer.y);
        writer.stream.lineTo(MARGIN + SIGNATURE_MAX_WIDTH + 40, writer.y);
        writer.stream.stroke();
        writer.skip(4);

        writer.paragraph(block.fullName(), bold, BODY_SIZE);
        writer.paragraph(block.documentLabel(), regular, BODY_SIZE);
        writer.paragraph("Grupo: " + block.groupName(), regular, BODY_SIZE);
        writer.paragraph("Firmado el " + DATE_TIME.format(block.acceptedAt().atZone(LIMA))
                + " (hora de Lima)", regular, BODY_SIZE);
        writer.skip(BODY_SIZE * 0.6f);
        writer.paragraph("Huella del texto (SHA-256): " + block.textHash(), regular, 7.5f);
    }

    private void writeFooters(PDDocument document, String version) throws IOException {
        var total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            var page = document.getPage(i);
            try (var stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                var text = "PocketPeers - Declaración jurada v%s - Página %d de %d".formatted(version, i + 1, total);
                stream.beginText();
                stream.setFont(regular, 8);
                stream.newLineAtOffset(MARGIN, MARGIN / 2);
                stream.showText(text);
                stream.endText();
            }
        }
    }

    /** Reemplaza lo que Helvetica no puede codificar, en vez de fallar. */
    private String sanitize(String text) {
        var result = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            var character = new String(Character.toChars(codePoint));
            try {
                regular.encode(character);
                result.append(character);
            } catch (IOException | IllegalArgumentException e) {
                result.append('?');
            }
        });
        return result.toString();
    }

    /** Escribe de arriba hacia abajo y abre pagina nueva cuando se acaba la actual. */
    private final class PageWriter {
        private final PDDocument document;
        private final float width = PDRectangle.A4.getWidth() - 2 * MARGIN;
        private PDPageContentStream stream;
        private float y;

        PageWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void paragraph(String text, PDType1Font font, float size) throws IOException {
            for (var line : wrap(sanitize(text), font, size)) {
                ensureSpace(size * LEADING);
                y -= size * LEADING;
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(line);
                stream.endText();
            }
        }

        void skip(float amount) {
            y -= amount;
        }

        void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        void close() throws IOException {
            stream.close();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private List<String> wrap(String text, PDType1Font font, float size) throws IOException {
            var lines = new ArrayList<String>();
            var current = new StringBuilder();
            for (var word : text.split(" ")) {
                var candidate = current.isEmpty() ? word : current + " " + word;
                if (current.isEmpty() || font.getStringWidth(candidate) / 1000 * size <= width) {
                    current = new StringBuilder(candidate);
                } else {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                }
            }
            lines.add(current.toString());
            return lines;
        }
    }
}

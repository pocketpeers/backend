package com.pocketpeers.backend.groups.application.internal.declarations;

import static org.assertj.core.api.Assertions.assertThat;

import com.pocketpeers.backend.groups.domain.model.valueobjects.MembershipDeclarationTemplate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

class MembershipDeclarationPdfRendererTests {

    @Test
    void pdfContainsTheDeclarationTheSignerDataAndTheDrawnSignature() throws IOException {
        var text = MembershipDeclarationTemplate.render("Ana Pérez Ñahui", "DNI", "12345678", "Viaje a Cusco");
        var pdf = new MembershipDeclarationPdfRenderer().render(text, SignatureImageTests.signature(),
                new MembershipDeclarationPdfRenderer.SignatureBlock("Ana Pérez Ñahui", "DNI N.° 12345678",
                        "Viaje a Cusco", "1.0", Instant.parse("2026-09-25T19:30:00Z"), "ab".repeat(32)));

        try (var document = Loader.loadPDF(pdf)) {
            var content = new PDFTextStripper().getText(document);
            assertThat(content)
                    .contains("DECLARACIÓN JURADA")
                    .contains("Ana Pérez Ñahui")
                    .contains("DNI N.° 12345678")
                    .contains("Viaje a Cusco")
                    .contains("25/09/2026 14:30:00")
                    .contains("Página 1 de");

            var lastPage = document.getPage(document.getNumberOfPages() - 1);
            var images = 0;
            for (var name : lastPage.getResources().getXObjectNames()) {
                if (lastPage.getResources().getXObject(name) instanceof PDImageXObject) {
                    images++;
                }
            }
            assertThat(images).as("la firma dibujada va incrustada").isEqualTo(1);
        }
    }
}

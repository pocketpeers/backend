package com.pocketpeers.backend.operations.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.pocketpeers.backend.operations.domain.model.commands.CreateOcrReceiptFromImageCommand;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.OcrReceipt;
import com.pocketpeers.backend.operations.domain.model.valueobjects.OcrData;
import com.pocketpeers.backend.operations.domain.ports.out.ReceiptOcrPort;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ReceiptRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import com.pocketpeers.backend.shared.domain.model.entities.Image;
import com.pocketpeers.backend.shared.domain.services.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * El aviso de duplicado tiene que llegar en la lectura del OCR.
 *
 * <p>Antes solo existia en el registro del comprobante, que ocurre despues de
 * crear el gasto y repartir los pagos: para cuando avisaba, ya no habia nada
 * que evitar.</p>
 */
class ReceiptOcrDuplicateTests {

    private static final String IMAGE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SHA = "abc123";

    private ReceiptRepository receiptRepository;
    private ImageService imageService;
    private ReceiptOcrPort receiptOcrPort;
    private ReceiptCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        receiptRepository = Mockito.mock(ReceiptRepository.class);
        imageService = Mockito.mock(ImageService.class);
        receiptOcrPort = Mockito.mock(ReceiptOcrPort.class);
        service = new ReceiptCommandServiceImpl(
                receiptRepository, Mockito.mock(ExpenseRepository.class), receiptOcrPort, imageService);

        var image = Mockito.mock(Image.class);
        when(image.getSha256()).thenReturn(SHA);
        when(image.getPerceptualHash()).thenReturn(null);
        when(imageService.findImageById(any(UUID.class))).thenReturn(Optional.of(image));

        var ocr = new OcrReceipt("Boleta", "B001-123", "20123456789",
                BigDecimal.TEN, LocalDate.now(), IMAGE_ID, new OcrData(Map.of()));
        when(receiptOcrPort.processReceiptImage(anyString())).thenReturn(ocr);
    }

    @Test
    @DisplayName("La lectura OCR avisa cuando la misma imagen ya respalda otro gasto")
    void avisaImagenRepetida() {
        var existing = Mockito.mock(ExpenseReceipt.class);
        when(existing.getId()).thenReturn(7L);
        when(receiptRepository.findByImageSha256(SHA)).thenReturn(List.of(existing));

        var preview = service.handle(new CreateOcrReceiptFromImageCommand(IMAGE_ID));

        assertThat(preview.duplicate().isDuplicate()).isTrue();
        assertThat(preview.duplicate().conflictingReceiptId()).isEqualTo(7L);
        assertThat(preview.duplicate().message()).contains("ya fue registrado");
        assertThat(preview.receipt()).isNotNull();
    }

    @Test
    @DisplayName("Sin conflicto el veredicto viene limpio y el OCR igual llega")
    void sinConflicto() {
        when(receiptRepository.findByImageSha256(anyString())).thenReturn(List.of());
        when(receiptRepository.findByIssuerRucAndDocumentNumber(anyString(), anyString()))
                .thenReturn(List.of());

        var preview = service.handle(new CreateOcrReceiptFromImageCommand(IMAGE_ID));

        assertThat(preview.duplicate().isDuplicate()).isFalse();
        assertThat(preview.duplicate().message()).isNull();
        assertThat(preview.receipt().getName()).isEqualTo("Boleta");
    }
}

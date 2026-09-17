package com.pocketpeers.backend.operations.application.internal.commandservices;


import com.pocketpeers.backend.operations.domain.exceptions.DuplicateReceiptException;
import com.pocketpeers.backend.operations.domain.exceptions.ExpenseNotFoundException;
import com.pocketpeers.backend.operations.domain.exceptions.ReceiptNotFoundException;
import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.commands.*;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.OcrReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.Receipt;
import com.pocketpeers.backend.operations.domain.ports.out.ReceiptOcrPort;
import com.pocketpeers.backend.operations.domain.services.ReceiptCommandService;
import com.pocketpeers.backend.operations.domain.model.valueobjects.DuplicateReceiptCheck;
import com.pocketpeers.backend.operations.domain.model.valueobjects.OcrReceiptPreview;
import com.pocketpeers.backend.operations.domain.services.ReceiptDuplicateDetector;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ReceiptRepository;
import com.pocketpeers.backend.shared.domain.model.valueobjects.ImageFingerprint;
import com.pocketpeers.backend.shared.domain.services.ImageService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ReceiptCommandServiceImpl implements ReceiptCommandService {
    private ReceiptRepository receiptRepository;
    private ExpenseRepository expenseRepository;
    private ReceiptOcrPort receiptOcrPort;
    private ImageService imageService;

    @Override
    public Receipt handle(CreateReceiptForExpenseCommand command) {
        Expense expense = expenseRepository.findById(command.expenseId())
                .orElseThrow(()-> new ExpenseNotFoundException(command.expenseId()));

        String receiptNumber = normalize(command.receiptNumber());
        String issuerRuc = normalize(command.issuerRuc());
        ImageFingerprint fingerprint = fingerprintOf(command.imagePath());

        rejectIfDuplicate(fingerprint, issuerRuc, receiptNumber);

        ExpenseReceipt receipt = new ExpenseReceipt(
                command.name(),
                receiptNumber,
                command.amount(),
                command.issueDate(),
                command.imagePath(),
                expense
        );

        // Validate that the receipt amount does not exceed the payment amount
        if(expense.getAmount().compareTo(receipt.getAmount()) < 0) {
            throw new IllegalArgumentException("Receipt amount cannot exceed payment amount." +
                    " Expense amount is: " + expense.getAmount() +
                    ", Receipt amount is: " + receipt.getAmount());
        }

        //Validate that sum all receipts for this payment does not exceed the payment amount
        List<ExpenseReceipt> receipts = expense.getReceipts();
        BigDecimal totalReceiptsAmount = receipts.stream()
                .map(Receipt::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingAmount = expense.getAmount().subtract(totalReceiptsAmount);
        if (remainingAmount.compareTo(receipt.getAmount()) < 0) {
            throw new IllegalArgumentException("Total receipts amount cannot exceed expense amount."+
                    " Remaining amount for this expense is: " + remainingAmount);
        }

        receipt.sealFingerprint(issuerRuc, fingerprint.sha256(), fingerprint.perceptualHash());
        flagIfVisuallySimilar(receipt, fingerprint);
        receiptRepository.save(receipt);
        return receipt;
    }

    /**
     * Anota el parecido visual con un comprobante ya registrado, sin impedir el
     * registro.
     *
     * <p>Se midio sobre documentos sinteticos y las dos poblaciones se solapan:
     * entre boletas distintas la distancia bajo a 2, y la misma imagen reenviada
     * con recompresion llego a 3. Con las bandas superpuestas, cualquier corte
     * que atrape todos los reenvios rechaza tambien documentos legitimos, y
     * hashes mas largos no separan —escalan las dos distancias por igual—. Por
     * eso esta senal informa y no decide: el rechazo lo sostienen el SHA-256 y
     * la llave RUC + numero, que son igualdades.</p>
     */
    private void flagIfVisuallySimilar(ExpenseReceipt receipt, ImageFingerprint fingerprint) {
        if (!fingerprint.hasPerceptualHash()) {
            return;
        }
        ReceiptDuplicateDetector
                .findSuspectedDuplicate(fingerprint.perceptualHash(), receiptRepository.findPerceptualHashes())
                .ifPresent(match -> receipt.flagSuspectedDuplicateOf(match.receiptId()));
    }

    /**
     * Huella de la imagen ya subida, leida del servidor y no del cliente.
     *
     * <p>Este es el punto en que el control deja de ser cosmetico. La app envia
     * un identificador de imagen, no una huella: el servidor la calculo al
     * recibir el archivo y la busca por si mismo, asi que un cliente modificado
     * no puede declarar una huella falsa para esquivar la deteccion. Lo unico
     * que lograria enviando un identificador inventado es quedarse sin imagen.</p>
     *
     * <p>Un identificador ausente, mal formado o de una imagen ya borrada no es
     * un error: devuelve una huella vacia y el comprobante se registra sin esa
     * senal. Bloquear ahi castigaria al usuario honesto por un dato que el
     * sistema no supo conservar.</p>
     */
    private ImageFingerprint fingerprintOf(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return ImageFingerprint.none();
        }
        try {
            return imageService.findImageById(UUID.fromString(imagePath.trim()))
                    .map(image -> new ImageFingerprint(image.getSha256(), image.getPerceptualHash()))
                    .orElseGet(ImageFingerprint::none);
        } catch (IllegalArgumentException notAUuid) {
            return ImageFingerprint.none();
        }
    }

    /**
     * Las dos senales que sostienen un rechazo. Ambas son igualdades exactas.
     *
     * <p>El SHA-256 lo deriva el servidor de los bytes almacenados, asi que un
     * cliente modificado no puede declarar una huella falsa para esquivarlo.
     * Atrapa el reenvio del mismo archivo, que es la forma comun de reusar una
     * boleta.</p>
     *
     * <p>La llave RUC + numero viene del OCR y el cliente podria alterarla.
     * Existe igual porque es la unica que atrapa la boleta <i>vuelta a
     * fotografiar</i>, cuyo archivo es genuinamente distinto y a la que la
     * huella de imagen es ciega. Que sea falsificable no la vuelve inutil:
     * cubre el caso honesto y obliga a quien defrauda a manipular el cliente.</p>
     *
     * <p>El parecido visual no esta aqui a proposito. Ver
     * {@code flagIfVisuallySimilar}.</p>
     */
    private void rejectIfDuplicate(ImageFingerprint fingerprint, String issuerRuc, String receiptNumber) {
        DuplicateReceiptCheck check = findDuplicate(fingerprint, issuerRuc, receiptNumber);
        if (check.isDuplicate()) {
            throw check.toException();
        }
    }

    /**
     * Las mismas dos consultas, devueltas en vez de lanzadas.
     *
     * <p>La comprobacion vive aqui una sola vez porque la usan dos rutas: el
     * registro del comprobante, que aborta, y la lectura con OCR, que solo avisa.
     * Tenerlas separadas fue justamente lo que dejo pasar el caso: el OCR no
     * miraba nada y el rechazo llegaba cuando el gasto y sus pagos ya existian.</p>
     */
    private DuplicateReceiptCheck findDuplicate(ImageFingerprint fingerprint, String issuerRuc, String receiptNumber) {
        if (fingerprint.hasSha256()) {
            var bySha = receiptRepository.findByImageSha256(fingerprint.sha256()).stream().findFirst();
            if (bySha.isPresent()) {
                return checkOf(DuplicateReceiptException.Signal.IMAGE_EXACT, bySha.get());
            }
        }

        String documentNumber = ExpenseReceipt.canonicalDocumentNumber(receiptNumber);
        if (issuerRuc != null && documentNumber != null) {
            var byNumber = receiptRepository.findByIssuerRucAndDocumentNumber(issuerRuc, documentNumber)
                    .stream().findFirst();
            if (byNumber.isPresent()) {
                return checkOf(DuplicateReceiptException.Signal.DOCUMENT_NUMBER, byNumber.get());
            }
        }

        return DuplicateReceiptCheck.none();
    }

    private DuplicateReceiptCheck checkOf(DuplicateReceiptException.Signal signal, ExpenseReceipt existing) {
        return new DuplicateReceiptCheck(
                signal,
                existing.getId(),
                existing.getExpense() == null ? null : existing.getExpense().getId());
    }

    private DuplicateReceiptException duplicate(DuplicateReceiptException.Signal signal, ExpenseReceipt existing) {
        return new DuplicateReceiptException(
                signal,
                existing.getId(),
                existing.getExpense() == null ? null : existing.getExpense().getId());
    }

    /**
     * Vacio es lo mismo que ausente.
     *
     * <p>La app envia cadena vacia cuando el OCR no encontro el dato. Guardarla
     * tal cual haria que el indice unico tratara todos esos comprobantes como el
     * mismo documento, y el segundo usuario con una foto ilegible quedaria
     * bloqueado por el primero.</p>
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public OcrReceipt handle(CreateOcrReceiptFromReceiptCommand command) {
        var receipt = receiptRepository.findById(command.originalReceiptId())
                .orElseThrow(()-> new ReceiptNotFoundException(command.originalReceiptId()));

        if(receipt.getImagePath()==null) {
            throw new IllegalArgumentException("Receipt image path is not set for OCR processing.");
        }

        if(receipt.getOcrReceipt() != null) {
            return receipt.getOcrReceipt(); // Return existing OCR receipt if it already exists
        }

        OcrReceipt ocrReceipt = receiptOcrPort.processReceiptImage(receipt.getImagePath());
        ocrReceipt.assignToOriginalReceipt(receipt);
        receiptRepository.save(ocrReceipt);
        return ocrReceipt;
    }

    @Override
    public OcrReceiptPreview handle(CreateOcrReceiptFromImageCommand command) {
        OcrReceipt receipt = receiptOcrPort.processReceiptImage(command.imageUrl());

        // El control corre aqui, con la imagen ya subida y el OCR ya leido, que
        // es el primer instante en que estan las dos senales: el SHA-256 lo
        // tiene el servidor desde la subida y el RUC + numero acaba de salir del
        // OCR. Antes de este punto no se puede decidir, y despues ya es tarde:
        // el siguiente paso de la app crea el gasto con todos sus pagos.
        DuplicateReceiptCheck duplicate = findDuplicate(
                fingerprintOf(command.imageUrl()),
                normalize(receipt.getIssuerRuc()),
                normalize(receipt.getReceiptNumber()));

        return OcrReceiptPreview.of(receipt, duplicate);
    }

    @Override
    public void handle(DeleteReceiptCommand command) {
        Receipt receipt = receiptRepository.findById(command.receiptId())
                .orElseThrow(() -> new ReceiptNotFoundException(command.receiptId()));

        receiptRepository.delete(receipt);
    }
}

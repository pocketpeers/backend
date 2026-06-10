package com.pocketpeers.backend.operations.interfaces.rest;

import com.pocketpeers.backend.operations.domain.model.commands.CreateOcrReceiptFromImageCommand;
import com.pocketpeers.backend.operations.domain.model.commands.CreateOcrReceiptFromReceiptCommand;
import com.pocketpeers.backend.operations.domain.services.ReceiptCommandService;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreateOcrReceiptFromImageResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ReceiptOcrResource;
import com.pocketpeers.backend.operations.interfaces.rest.transform.ReceiptResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "OCR Receipts", description = "OCR Receipts management endpoints")
@RestController
@RequestMapping(value = "/api/v1/ocr-receipt", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ReceiptOcrController {

    private final ReceiptCommandService receiptCommandService;

    @Operation(summary = "Create a new OCR receipt from an existing receipt")
    @PostMapping("/from-receipt/{receiptId}")
    public ResponseEntity<ReceiptOcrResource> getReceiptOcrFromReceipt(@PathVariable Long receiptId) {
        var command = new CreateOcrReceiptFromReceiptCommand(receiptId);
        var receiptOcr = receiptCommandService.handle(command);
        var receiptOcrResource = ReceiptResourceFromEntityAssembler.toResourceFromEntity(receiptOcr);
        return ResponseEntity.ok(receiptOcrResource);
    }

    @Operation(summary = "Create a new OCR receipt from an imageId without persisting the result")
    @PostMapping("/from-image")
    public ResponseEntity<ReceiptOcrResource> getReceiptOcrFromImageUrl(@RequestBody CreateOcrReceiptFromImageResource resource) {
        var command = new CreateOcrReceiptFromImageCommand(resource.imageId());
        var receiptOcr = receiptCommandService.handle(command);
        var receiptOcrResource = ReceiptResourceFromEntityAssembler.toResourceFromEntity(receiptOcr);
        return ResponseEntity.ok(receiptOcrResource);
    }
}

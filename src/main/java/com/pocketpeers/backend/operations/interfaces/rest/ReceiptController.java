package com.pocketpeers.backend.operations.interfaces.rest;

import com.pocketpeers.backend.operations.domain.exceptions.ReceiptNotFoundException;
import com.pocketpeers.backend.operations.domain.model.commands.CreateOcrReceiptFromReceiptCommand;
import com.pocketpeers.backend.operations.domain.model.commands.DeleteReceiptCommand;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllReceiptsByExpenseIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllReceiptsByPaymentIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetReceiptByIdQuery;
import com.pocketpeers.backend.operations.domain.services.ReceiptCommandService;
import com.pocketpeers.backend.operations.domain.services.ReceiptQueryService;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreateExpenseReceiptResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreatePaymentReceiptResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ReceiptOcrResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ReceiptResource;
import com.pocketpeers.backend.operations.interfaces.rest.transform.CreateReceiptCommandFromResourceAssembler;
import com.pocketpeers.backend.operations.interfaces.rest.transform.ReceiptResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Receipts", description = "Receipts management endpoints")
@RestController
@AllArgsConstructor
@RequestMapping(value="/api/v1/receipts", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReceiptController {
    private ReceiptQueryService receiptQueryService;
    private ReceiptCommandService receiptCommandService;

    @GetMapping("/{receiptId}")
    @Operation(summary = "Get receipt by id")
    public ResponseEntity<ReceiptResource> getReceiptById(@PathVariable Long receiptId){
        var query = new GetReceiptByIdQuery(receiptId);
        var receipt = receiptQueryService.handle(query).orElseThrow(()->new ReceiptNotFoundException(receiptId));
        var receiptResouce = ReceiptResourceFromEntityAssembler.toResourceFromEntity(receipt);

        return ResponseEntity.ok(receiptResouce);
    }

    @GetMapping("/expense/{expenseId}")
    @Operation(summary = "Get all receipts by expenseId")
    public ResponseEntity<List<ReceiptResource>> getReceiptsByExpenseId(@PathVariable Long expenseId){
        var query = new GetAllReceiptsByExpenseIdQuery(expenseId);
        var receipts = receiptQueryService.handle(query);
        var receiptsResources = receipts.stream().map(ReceiptResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(receiptsResources);
    }

    @PostMapping("/expense")
    @Operation(summary = "Create a new receipt for a expense")
    public ResponseEntity<ReceiptResource> createExpenseReceipt(@Validated @RequestBody CreateExpenseReceiptResource resource){
        var command = CreateReceiptCommandFromResourceAssembler.toCommandFromResource(resource);
        var receipt = receiptCommandService.handle(command);
        var receiptResouce = ReceiptResourceFromEntityAssembler.toResourceFromEntity(receipt);

        return new ResponseEntity(receiptResouce, HttpStatus.CREATED);
    }

    @DeleteMapping("/{receiptId}")
    @Operation(summary = "Delete a receipt by id")
    public ResponseEntity<Void> deleteReceiptById(@PathVariable Long receiptId) {
        var command = new DeleteReceiptCommand(receiptId);
        receiptCommandService.handle(command);
        return ResponseEntity.noContent().build();
    }

}

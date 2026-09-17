package com.pocketpeers.backend.operations.interfaces.rest.transform;

import com.pocketpeers.backend.operations.domain.model.commands.CreateReceiptForExpenseCommand;
import com.pocketpeers.backend.operations.domain.model.commands.CreateReceiptForPaymentCommand;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreateExpenseReceiptResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreatePaymentReceiptResource;

public class CreateReceiptCommandFromResourceAssembler {
    public static CreateReceiptForPaymentCommand toCommandFromResource(CreatePaymentReceiptResource receipt) {
        return new CreateReceiptForPaymentCommand(
                receipt.name(),
                receipt.receiptNumber(),
                receipt.amount(),
                receipt.issueDate(),
                receipt.imagePath(),
                receipt.paymentId()
        );
    }

    public static CreateReceiptForExpenseCommand toCommandFromResource(CreateExpenseReceiptResource receipt) {
        return new CreateReceiptForExpenseCommand(
                receipt.name(),
                receipt.receiptNumber(),
                receipt.issuerRuc(),
                receipt.amount(),
                receipt.issueDate(),
                receipt.imagePath(),
                receipt.expenseId()
        );
    }
}

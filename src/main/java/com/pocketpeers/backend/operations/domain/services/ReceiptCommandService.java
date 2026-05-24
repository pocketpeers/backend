package com.pocketpeers.backend.operations.domain.services;

import com.pocketpeers.backend.operations.domain.model.commands.*;
import com.pocketpeers.backend.operations.domain.model.entities.OcrReceipt;
import com.pocketpeers.backend.operations.domain.model.entities.Receipt;

public interface ReceiptCommandService {
    Receipt handle(CreateReceiptForPaymentCommand command);
    Receipt handle(CreateReceiptForExpenseCommand command);
    void handle(DeleteReceiptCommand command);
}

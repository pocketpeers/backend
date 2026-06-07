package com.pocketpeers.backend.operations.domain.ports.out;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;

public interface ExpenseSmartContractPort {
    ContractAddress deployExpenseContract(Expense expense)  throws Exception;
    TransactionHash addPaymentToExpenseContract(Expense expense, Payment payment)  throws Exception;
    TransactionHash updatePaymentStatus(Payment payment, PaymentStatus status)  throws Exception;
}

package com.pocketpeers.backend.operations.application.internal.eventservices;

import com.pocketpeers.backend.operations.domain.model.events.ExpenseCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentCreatedEvent;
import com.pocketpeers.backend.operations.domain.model.events.PaymentUpdatedEvent;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;
import com.pocketpeers.backend.operations.domain.ports.out.ExpenseSmartContractPort;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;


@Component
@AllArgsConstructor
public class ExpenseContractEventHandler {
    private ExpenseSmartContractPort expenseSmartContractPort;

    @EventListener(ExpenseCreatedEvent.class)
    @Async
    public void handler(ExpenseCreatedEvent event) throws Exception {

        System.out.println("Handling ExpenseCreatedEvent for expense: " + event.expense().getName());

        ContractAddress contractAddress = expenseSmartContractPort.deployExpenseContract(event.expense());

        System.out.println("Expense contract deployed with address: " + contractAddress.address());
    }

    @EventListener(PaymentCreatedEvent.class)
    @Async
    public void handler(PaymentCreatedEvent event) throws Exception {
        TransactionHash transactionHash = expenseSmartContractPort.addPaymentToExpenseContract(
                        event.payment().getExpense(),
                        event.payment()
                );
        System.out.println("Payment added to expense contract with transaction hash: " + transactionHash.hash());
    }

    @EventListener(PaymentUpdatedEvent.class)
    @Async
    public void handler(PaymentUpdatedEvent event) throws Exception{
        TransactionHash transactionHash = expenseSmartContractPort.updatePaymentStatus(
                event.payment(),
                PaymentStatus.valueOf(event.payment().getStatus())
        );
        System.out.println("Payment status updated in expense contract with transaction hash: " + transactionHash.hash());
    }

}

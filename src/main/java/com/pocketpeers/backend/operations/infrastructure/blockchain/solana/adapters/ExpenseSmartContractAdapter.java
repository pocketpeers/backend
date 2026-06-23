package com.pocketpeers.backend.operations.infrastructure.blockchain.solana.adapters;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.entities.ContractTransaction;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseContract;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;
import com.pocketpeers.backend.operations.domain.ports.out.ExpenseSmartContractPort;
import com.pocketpeers.backend.operations.infrastructure.blockchain.solana.services.SolanaClient;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ContractTransactionRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseContractRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.SystemProgram;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseSmartContractAdapter implements ExpenseSmartContractPort {
    private static final String MEMO_PROGRAM_ID = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr";
    private static final Duration SIGNATURE_STATUS_TIMEOUT = Duration.ofSeconds(90);
    private static final long SIGNATURE_STATUS_POLL_MILLIS = 1_500;

    private final SolanaClient solanaClient;
    private final ExpenseContractRepository expenseContractRepository;
    private final ContractTransactionRepository contractTransactionRepository;

    private long obtenerSaldoBackend() throws Exception {
        PublicKey walletKey = solanaClient.getSignerAccount().getPublicKey();
        long saldoEnLamports = solanaClient.getRpcClient().getApi().getBalance(walletKey);
        System.out.println("El saldo de la wallet es: " + (saldoEnLamports / 1_000_000_000.0) + " SOL");
        return saldoEnLamports;
    }

    @Override
    @Transactional
    public ContractAddress deployExpenseContract(Expense expense) throws Exception {
        expenseContractRepository.findByExpense(expense)
                .ifPresent(existingContract -> {
                    throw new IllegalArgumentException("Expense contract already exists for the given expense");
                });

        PublicKey memoProgramPublicKey = new PublicKey(MEMO_PROGRAM_ID);
        PublicKey feePayerPublicKey = solanaClient.getSignerAccount().getPublicKey();

        String signature;
        try {
            String latestBlockhash = latestBlockhash();

            Transaction transaction = new Transaction();

            // 1. Añadimos una micro-transferencia a nosotros mismos para activar la Tx en la red (Costo: 1000 Lamports)
            transaction.addInstruction(SystemProgram.transfer(
                    feePayerPublicKey,
                    feePayerPublicKey,
                    1_000
            ));

            // 2. Adjuntamos el JSON metadata del gasto usando el Memo Program
            String memoJson = String.format("{\"action\":\"CREATE_EXPENSE\",\"id\":%d,\"amount\":%d,\"name\":\"%s\"}",
                    expense.getId(), expense.getAmount().longValue(), expense.getName());

            transaction.addInstruction(new TransactionInstruction(
                    memoProgramPublicKey,
                    Collections.emptyList(), // El programa de Memos no requiere llaves obligatorias
                    memoJson.getBytes(StandardCharsets.UTF_8)
            ));

            transaction.setRecentBlockHash(latestBlockhash);

            signature = sendTransaction(transaction, latestBlockhash);
            System.out.println("Expense transaction recorded with signature: " + signature);
            waitForSuccessfulSignature(signature);
        } catch (Exception exception) {
            System.out.println("Failed to record expense on Solana: " + exception.getMessage());
            throw new RuntimeException("Failed to record expense on Solana: " + exception.getMessage(), exception);
        }

        // Guardamos el Hash de la firma como la dirección "virtual" del contrato
        ExpenseContract expenseContract = expenseContractRepository.save(new ExpenseContract(
                new ContractAddress(signature),
                expense
        ));

        ContractTransaction transaction = new ContractTransaction();
        transaction.setContract(expenseContract);
        transaction.setTransactionHash(new TransactionHash(signature));
        contractTransactionRepository.save(transaction);

        obtenerSaldoBackend();

        return expenseContract.getContractAddress();
    }

    @Override
    @Transactional
    public TransactionHash addPaymentToExpenseContract(Expense expense, Payment payment) throws Exception {
        ExpenseContract expenseContractEntity = expenseContractRepository.findByExpense(expense)
                .orElseThrow(() -> new RuntimeException("Expense contract not found for the given expense"));

        PublicKey memoProgramPublicKey = new PublicKey(MEMO_PROGRAM_ID);
        PublicKey feePayerPublicKey = solanaClient.getSignerAccount().getPublicKey();
        String signature;
        try {
            Transaction transaction = new Transaction();

            // Transferencia real del pago a nosotros mismos (o la wallet destino que decidas)
            transaction.addInstruction(SystemProgram.transfer(
                    feePayerPublicKey,
                    feePayerPublicKey,
                    1_000
            ));

            // Memo de auditoría vinculando el pago al hash del gasto original
            String memoJson = String.format("{\"action\":\"ADD_PAYMENT\",\"paymentId\":%d,\"expenseTx\":\"%s\",\"amount\":%d}",
                    payment.getId(), expenseContractEntity.getContractAddress().address(), payment.getAmount().longValue());

            transaction.addInstruction(new TransactionInstruction(
                    memoProgramPublicKey,
                    Collections.emptyList(),
                    memoJson.getBytes(StandardCharsets.UTF_8)
            ));

            String latestBlockhash = latestBlockhash();
            transaction.setRecentBlockHash(latestBlockhash);

            signature = sendTransaction(transaction, latestBlockhash);
            waitForSuccessfulSignature(signature);
        } catch (Exception exception) {
            System.out.println("Failed to add payment on Solana: " + exception.getMessage());
            throw new RuntimeException("Failed to add payment on Solana: " + exception.getMessage(), exception);
        }

        TransactionHash transactionHash = new TransactionHash(signature);

        ContractTransaction transaction = new ContractTransaction();
        transaction.setContract(expenseContractEntity);
        transaction.setPayment(payment);
        transaction.setTransactionHash(transactionHash);
        contractTransactionRepository.save(transaction);

        obtenerSaldoBackend();

        return transactionHash;
    }

    @Override
    @Transactional
    public TransactionHash updatePaymentStatus(Payment payment, PaymentStatus status) throws Exception {
        ExpenseContract expenseContractEntity = expenseContractRepository.findByExpense(payment.getExpense())
                .orElseThrow(() -> new Exception("Expense contract not found for the given payment"));

        PublicKey memoProgramPublicKey = new PublicKey(MEMO_PROGRAM_ID);
        PublicKey feePayerPublicKey = solanaClient.getSignerAccount().getPublicKey();
        String signature;
        try {
            Transaction transaction = new Transaction();

            transaction.addInstruction(SystemProgram.transfer(
                    feePayerPublicKey,
                    feePayerPublicKey,
                    1_000
            ));

            String memoJson = String.format("{\"action\":\"UPDATE_STATUS\",\"paymentId\":%d,\"status\":\"%s\"}",
                    payment.getId(), status.name());

            transaction.addInstruction(new TransactionInstruction(
                    memoProgramPublicKey,
                    Collections.emptyList(),
                    memoJson.getBytes(StandardCharsets.UTF_8)
            ));

            String latestBlockhash = latestBlockhash();
            transaction.setRecentBlockHash(latestBlockhash);

            signature = sendTransaction(transaction, latestBlockhash);
            waitForSuccessfulSignature(signature);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to update payment status on Solana: " + exception.getMessage(), exception);
        }

        TransactionHash transactionHash = new TransactionHash(signature);

        ContractTransaction contractTransaction = new ContractTransaction();
        contractTransaction.setContract(expenseContractEntity);
        contractTransaction.setPayment(payment);
        contractTransaction.setTransactionHash(transactionHash);
        contractTransactionRepository.save(contractTransaction);

        obtenerSaldoBackend();

        return transactionHash;
    }

    private String sendTransaction(Transaction transaction, String latestBlockhash) throws Exception {
        return solanaClient.getRpcClient().getApi().sendTransaction(
                transaction,
                List.of(solanaClient.getSignerAccount()),
                latestBlockhash
        );
    }

    private String latestBlockhash() throws Exception {
        Map<String, Object> blockhashResult = solanaClient.getRpcClient().call(
                "getLatestBlockhash",
                null,
                Map.class
        );
        Map<String, Object> valueMap = (Map<String, Object>) blockhashResult.get("value");
        return (String) valueMap.get("blockhash");
    }

    private void waitForSuccessfulSignature(String signature) throws Exception {
        long deadline = System.currentTimeMillis() + SIGNATURE_STATUS_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> result = solanaClient.getRpcClient().call(
                    "getSignatureStatuses",
                    signatureStatusParams(signature),
                    Map.class
            );
            List<?> values = (List<?>) result.get("value");
            if (values != null && !values.isEmpty() && values.get(0) instanceof Map<?, ?> status) {
                Object error = status.get("err");
                if (error != null) {
                    throw new RuntimeException("Solana transaction failed. signature=" + signature + ", err=" + error);
                }
                Object confirmationStatus = status.get("confirmationStatus");
                if ("confirmed".equals(confirmationStatus) || "finalized".equals(confirmationStatus)) {
                    return;
                }
            }
            Thread.sleep(SIGNATURE_STATUS_POLL_MILLIS);
        }
        throw new RuntimeException("Timed out waiting for Solana confirmation. signature=" + signature);
    }

    private List<Object> signatureStatusParams(String signature) {
        var signatures = new ArrayList<String>();
        signatures.add(signature);
        return List.of(signatures, Map.of("searchTransactionHistory", true));
    }
}

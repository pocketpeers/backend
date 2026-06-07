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
import lombok.RequiredArgsConstructor;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseSmartContractAdapter implements ExpenseSmartContractPort {

    private final SolanaClient solanaClient;
    private final ExpenseContractRepository expenseContractRepository;
    private final ContractTransactionRepository contractTransactionRepository;

    @Value("${solana.program.id}")
    private String programId;

    private long obtenerSaldoBackend() throws Exception {
        PublicKey walletKey = solanaClient.getSignerAccount().getPublicKey();
        long saldoEnLamports = solanaClient.getRpcClient().getApi().getBalance(walletKey);
        System.out.println("El saldo de la wallet es: " + (saldoEnLamports / 1_000_000_000.0) + " SOL");
        return saldoEnLamports;
    }

    @Override
    public ContractAddress deployExpenseContract(Expense expense) throws Exception {
        expenseContractRepository.findByExpense(expense)
                .ifPresent(existingContract -> {
                    throw new IllegalArgumentException("Expense contract already exists for the given expense");
                });

        Account expenseDataAccount = new Account();
        String signature;

        try {
            PublicKey programPublicKey = new PublicKey(programId);
            PublicKey feePayerPublicKey = solanaClient.getSignerAccount().getPublicKey();
            PublicKey dataAccountPublicKey = expenseDataAccount.getPublicKey();

            // Obtener el último blockhash mediante el método JSON-RPC estándar vigente
            Map<String, Object> blockhashResult = (Map<String, Object>) solanaClient.getRpcClient().call("getLatestBlockhash", null, Map.class);
            Map<String, Object> valueMap = (Map<String, Object>) blockhashResult.get("value");
            String latestBlockhash = (String) valueMap.get("blockhash");

            // Definir el tamaño del espacio en bytes y calcular los Lamports mínimos para Rent Exemption
            long space = 120;
            long lamports = solanaClient.getRpcClient().getApi().getMinimumBalanceForRentExemption(space);


            // Estructura oficial del System Instruction para CreateAccount:
            // [4 bytes Discriminator (0)] + [8 bytes Lamports] + [8 bytes Space] + [32 bytes ProgramOwner]
            ByteBuffer systemInstructionBuffer = ByteBuffer.allocate(52).order(ByteOrder.LITTLE_ENDIAN);
            systemInstructionBuffer.putInt(0);
            systemInstructionBuffer.putLong(lamports);
            systemInstructionBuffer.putLong(space);
            systemInstructionBuffer.put(programPublicKey.toByteArray()); // ID de Smart Contract en Rust

            TransactionInstruction createAccountInstruction = new TransactionInstruction(
                    programPublicKey,
                    List.of(
                            new AccountMeta(feePayerPublicKey, true, true),
                            new AccountMeta(dataAccountPublicKey, true, true)
                    ),
                    systemInstructionBuffer.array()
            );

            // Invocar al Smart Contract personalizado para inicializar el gasto
            byte[] instructionData = serializeExpenseData(expense);
            TransactionInstruction createExpenseInstruction = new TransactionInstruction(
                    programPublicKey,
                    List.of(
                            new AccountMeta(feePayerPublicKey, true, true),
                            new AccountMeta(dataAccountPublicKey, true, true)
                    ),
                    instructionData
            );

            // Construir la transacción con ambas instrucciones coordinadas en orden
            Transaction transaction = new Transaction();
            transaction.addInstruction(createAccountInstruction);
            transaction.addInstruction(createExpenseInstruction);
            transaction.setRecentBlockHash(latestBlockhash);

            // Agrupamos los firmantes en una lista para el envío limpio
            List<Account> signers = List.of(solanaClient.getSignerAccount(), expenseDataAccount);

            signature = solanaClient.getRpcClient().getApi().sendTransaction(
                    transaction,
                    signers,
                    latestBlockhash
            );
            System.out.println("Expense contract deployed with signature: " + signature);

        } catch (Exception e) {
            System.out.println("Failed to create Solana expense account: " + e.getMessage());
            throw new RuntimeException("Failed to create Solana expense account: " + e.getMessage(), e);
        }

        String accountAddressStr = expenseDataAccount.getPublicKey().toBase58();

        ExpenseContract expenseContract = new ExpenseContract(
                new ContractAddress(accountAddressStr),
                expense
        );

        ContractTransaction transaction = new ContractTransaction();
        transaction.setContract(expenseContract);
        transaction.setTransactionHash(new TransactionHash(signature));

        contractTransactionRepository.save(transaction);

        obtenerSaldoBackend();

        return new ContractAddress(accountAddressStr);
    }

    @Override
    public TransactionHash addPaymentToExpenseContract(Expense expense, Payment payment) throws Exception {
        ExpenseContract expenseContractEntity = expenseContractRepository.findByExpense(expense)
                .orElseThrow(() -> new RuntimeException("Expense contract not found for the given expense"));

        String signature;
        try {
            PublicKey expenseAccountKey = new PublicKey(expenseContractEntity.getContractAddress().address());
            byte[] instructionData = serializePaymentData(payment);

            TransactionInstruction addPaymentInstruction = new TransactionInstruction(
                    new PublicKey(programId),
                    List.of(
                            new AccountMeta(solanaClient.getSignerAccount().getPublicKey(), true, false),
                            new AccountMeta(expenseAccountKey, false, true)
                    ),
                    instructionData
            );

            Transaction transaction = new Transaction().addInstruction(addPaymentInstruction);
            signature = solanaClient.getRpcClient().getApi().sendTransaction(
                    transaction,
                    solanaClient.getSignerAccount()
            );

        } catch (Exception e) {
            System.out.println("Failed to add payment on Solana: " + e.getMessage());
            throw new RuntimeException("Failed to add payment on Solana: " + e.getMessage(), e);
        }

        TransactionHash transactionHash = new TransactionHash(signature);

        ContractTransaction transaction = new ContractTransaction();
        transaction.setContract(expenseContractEntity);
        transaction.setTransactionHash(transactionHash);

        contractTransactionRepository.save(transaction);

        return transactionHash;
    }

    @Override
    public TransactionHash updatePaymentStatus(Payment payment, PaymentStatus status) throws Exception {
        ExpenseContract expenseContractEntity = expenseContractRepository.findByExpense(payment.getExpense())
                .orElseThrow(() -> new Exception("Expense contract not found for the given payment"));

        PublicKey expenseAccountKey = new PublicKey(expenseContractEntity.getContractAddress().address());
        byte[] instructionData = serializeStatusData(payment.getId(), status);

        TransactionInstruction updateInstruction = new TransactionInstruction(
                new PublicKey(programId),
                List.of(
                        new AccountMeta(solanaClient.getSignerAccount().getPublicKey(), true, false),
                        new AccountMeta(expenseAccountKey, false, true)
                ),
                instructionData
        );

        Transaction transaction = new Transaction().addInstruction(updateInstruction);
        String signature = solanaClient.getRpcClient().getApi().sendTransaction(
                transaction,
                solanaClient.getSignerAccount()
        );

        TransactionHash transactionHash = new TransactionHash(signature);

        ContractTransaction contractTransaction = new ContractTransaction();
        contractTransaction.setContract(expenseContractEntity);
        contractTransaction.setTransactionHash(transactionHash);

        contractTransactionRepository.save(contractTransaction);

        return transactionHash;
    }

    private byte[] serializeExpenseData(Expense expense) {
        ByteBuffer buffer = ByteBuffer.allocate(120).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0);
        buffer.putLong(expense.getId());
        buffer.putLong(expense.getAmount().longValue());
        buffer.putLong(expense.getUserInformation().getId());
        buffer.putLong(expense.getDueDate().toEpochDay());
        buffer.putLong(expense.getGroup().getId());
        return buffer.array();
    }

    private byte[] serializePaymentData(Payment payment) {
        ByteBuffer buffer = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 1);
        buffer.putLong(payment.getId());
        buffer.putLong(payment.getAmount().longValue());
        buffer.putLong(payment.getUserInformation().getId());
        return buffer.array();
    }

    private byte[] serializeStatusData(Long paymentId, PaymentStatus status) {
        ByteBuffer buffer = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 2);
        buffer.putLong(paymentId);
        buffer.put((byte) status.ordinal());
        return buffer.array();
    }
}
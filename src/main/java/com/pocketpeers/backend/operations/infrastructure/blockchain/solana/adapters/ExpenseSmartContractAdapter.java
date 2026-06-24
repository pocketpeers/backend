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
import com.pocketpeers.backend.operations.infrastructure.blockchain.solana.utils.SecurityUtils;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ContractTransactionRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseContractRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.SystemProgram;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseSmartContractAdapter implements ExpenseSmartContractPort {
    private static final byte[] EXPENSE_SEED = "expense".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PAYMENT_SEED = "payment".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_EXPENSE_NAME_BYTES = 64;
    private static final Duration SIGNATURE_STATUS_TIMEOUT = Duration.ofSeconds(90);
    private static final long SIGNATURE_STATUS_POLL_MILLIS = 1_500;
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

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
    @Transactional
    public ContractAddress deployExpenseContract(Expense expense) throws Exception {
        expenseContractRepository.findByExpense(expense)
                .ifPresent(existingContract -> {
                    throw new IllegalArgumentException("Expense contract already exists for the given expense");
                });

        PublicKey programPublicKey = new PublicKey(programId);
        PublicKey authority = solanaClient.getSignerAccount().getPublicKey();
        PublicKey expensePda = expensePda(expense.getId(), programPublicKey);

        String signature;
        try {
            String latestBlockhash = latestBlockhash();
            Transaction transaction = new Transaction();
            transaction.addInstruction(createExpenseInstruction(programPublicKey, authority, expensePda, expense));
            transaction.setRecentBlockHash(latestBlockhash);

            signature = sendTransaction(transaction, latestBlockhash);
            System.out.println("Expense account created on Solana. pda=" + expensePda.toBase58()
                    + ", signature=" + signature);
            waitForSuccessfulSignature(signature);
        } catch (Exception exception) {
            System.out.println("Failed to create expense on Solana: " + exception.getMessage());
            throw new RuntimeException("Failed to create expense on Solana: " + exception.getMessage(), exception);
        }

        ExpenseContract expenseContract = expenseContractRepository.save(new ExpenseContract(
                new ContractAddress(expensePda.toBase58()),
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
        var existingPaymentTransaction = contractTransactionRepository.findFirstByPayment_IdOrderByCreatedAtDesc(payment.getId());
        if (existingPaymentTransaction.isPresent()) {
            return existingPaymentTransaction.get().getTransactionHash();
        }

        ExpenseContract expenseContractEntity = expenseContractRepository.findByExpense(expense)
                .orElseThrow(() -> new RuntimeException("Expense contract not found for the given expense"));

        PublicKey programPublicKey = new PublicKey(programId);
        PublicKey authority = solanaClient.getSignerAccount().getPublicKey();
        PublicKey expensePda = new PublicKey(expenseContractEntity.getContractAddress().address());
        PublicKey paymentPda = paymentPda(expensePda, payment.getId(), programPublicKey);

        String signature;
        try {
            String latestBlockhash = latestBlockhash();
            Transaction transaction = new Transaction();
            transaction.addInstruction(registerPaymentInstruction(
                    programPublicKey,
                    authority,
                    expensePda,
                    paymentPda,
                    payment
            ));
            transaction.setRecentBlockHash(latestBlockhash);

            signature = sendTransaction(transaction, latestBlockhash);
            waitForSuccessfulSignature(signature);
        } catch (Exception exception) {
            System.out.println("Failed to register payment on Solana: " + exception.getMessage());
            throw new RuntimeException("Failed to register payment on Solana: " + exception.getMessage(), exception);
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

        PublicKey programPublicKey = new PublicKey(programId);
        PublicKey authority = solanaClient.getSignerAccount().getPublicKey();
        PublicKey expensePda = new PublicKey(expenseContractEntity.getContractAddress().address());
        PublicKey paymentPda = paymentPda(expensePda, payment.getId(), programPublicKey);

        String signature;
        try {
            String latestBlockhash = latestBlockhash();
            Transaction transaction = new Transaction();
            transaction.addInstruction(updatePaymentInstruction(
                    programPublicKey,
                    authority,
                    expensePda,
                    paymentPda,
                    payment,
                    status
            ));
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

    private TransactionInstruction createExpenseInstruction(
            PublicKey programPublicKey,
            PublicKey authority,
            PublicKey expensePda,
            Expense expense
    ) throws Exception {
        String hashedExpenseName = hashStringToSHA256(expense.getName());
        var data = new AnchorData("create_expense")
                .u64(expense.getId())
                .u64(expense.getGroup().getId())
                .u64(expense.getUser().getId())
                .u64(toMinorUnits(expense.getAmount()))
                .i64(expense.getDueDate().atTime(LocalTime.MIDNIGHT).atZone(LIMA_ZONE).toEpochSecond())
                .string(trimUtf8(hashedExpenseName, MAX_EXPENSE_NAME_BYTES))
                .toByteArray();

        return new TransactionInstruction(
                programPublicKey,
                List.of(
                        new AccountMeta(expensePda, false, true),
                        new AccountMeta(authority, true, true),
                        new AccountMeta(SystemProgram.PROGRAM_ID, false, false)
                ),
                data
        );
    }

    private TransactionInstruction registerPaymentInstruction(
            PublicKey programPublicKey,
            PublicKey authority,
            PublicKey expensePda,
            PublicKey paymentPda,
            Payment payment
    ) throws Exception {
        var data = new AnchorData("register_payment")
                .u64(payment.getId())
                .u64(payment.getUser().getId())
                .u64(toMinorUnits(payment.getAmount()))
                .u64(toMinorUnits(payment.getAmountPaid()))
                .toByteArray();

        return new TransactionInstruction(
                programPublicKey,
                List.of(
                        new AccountMeta(expensePda, false, true),
                        new AccountMeta(paymentPda, false, true),
                        new AccountMeta(authority, true, true),
                        new AccountMeta(SystemProgram.PROGRAM_ID, false, false)
                ),
                data
        );
    }

    private TransactionInstruction updatePaymentInstruction(
            PublicKey programPublicKey,
            PublicKey authority,
            PublicKey expensePda,
            PublicKey paymentPda,
            Payment payment,
            PaymentStatus status
    ) throws Exception {
        var data = new AnchorData("update_payment")
                .u64(toMinorUnits(payment.getAmountPaid()))
                .u8(anchorPaymentStatus(status))
                .bool(payment.getConfirmed())
                .toByteArray();

        return new TransactionInstruction(
                programPublicKey,
                List.of(
                        new AccountMeta(expensePda, false, true),
                        new AccountMeta(paymentPda, false, true),
                        new AccountMeta(authority, true, false)
                ),
                data
        );
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

    private PublicKey expensePda(Long expenseId, PublicKey programPublicKey) {
        return PublicKey.findProgramAddress(
                List.of(EXPENSE_SEED, leU64(expenseId)),
                programPublicKey
        ).getAddress();
    }

    private PublicKey paymentPda(PublicKey expensePda, Long paymentId, PublicKey programPublicKey) {
        return PublicKey.findProgramAddress(
                List.of(PAYMENT_SEED, expensePda.toByteArray(), leU64(paymentId)),
                programPublicKey
        ).getAddress();
    }

    private byte[] leU64(Long value) {
        return ByteBuffer.allocate(Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(value)
                .array();
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    private int anchorPaymentStatus(PaymentStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case PARTIAL -> 1;
            case COMPLETED -> 2;
        };
    }

    private String trimUtf8(String value, int maxBytes) {
        if (value == null) {
            return "";
        }
        var normalized = value.trim();
        if (normalized.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return normalized;
        }
        var builder = new StringBuilder();
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            var next = builder.toString() + new String(Character.toChars(codePoint));
            if (next.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
                break;
            }
            builder.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private String hashStringToSHA256(String originalString) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(originalString.getBytes(StandardCharsets.UTF_8));

            // Convertir los bytes a formato Hexadecimal (String de 64 caracteres)
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            throw new RuntimeException("Error al calcular el hash SHA-256", e);
        }
    }

    private static class AnchorData {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        AnchorData(String instructionName) throws Exception {
            output.write(anchorDiscriminator(instructionName));
        }

        AnchorData u64(long value) {
            write(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());
            return this;
        }

        AnchorData i64(long value) {
            return u64(value);
        }

        AnchorData u8(int value) {
            output.write(value);
            return this;
        }

        AnchorData bool(boolean value) {
            output.write(value ? 1 : 0);
            return this;
        }

        AnchorData string(String value) {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            write(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.length).array());
            write(bytes);
            return this;
        }

        byte[] toByteArray() {
            return output.toByteArray();
        }

        private void write(byte[] bytes) {
            output.write(bytes, 0, bytes.length);
        }

        private static byte[] anchorDiscriminator(String instructionName) throws Exception {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(("global:" + instructionName).getBytes(StandardCharsets.UTF_8));
            var discriminator = new byte[8];
            System.arraycopy(digest, 0, discriminator, 0, discriminator.length);
            return discriminator;
        }
    }
}

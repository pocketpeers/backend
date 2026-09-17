package com.pocketpeers.backend.operations.infrastructure.blockchain.solana.adapters;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.entities.ContractTransaction;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseContract;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;
import com.pocketpeers.backend.operations.domain.exceptions.PermanentContractSyncException;
import com.pocketpeers.backend.operations.domain.ports.out.ExpenseSmartContractPort;
import com.pocketpeers.backend.operations.infrastructure.blockchain.solana.services.SolanaClient;
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
    // Seeds used to derive stable Program Derived Addresses (PDA) for each
    // expense and payment. Keeping these values unchanged is important because
    // the same seeds must be used by the on-chain Solana program.
    private static final byte[] EXPENSE_SEED = "expense".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PAYMENT_SEED = "payment".getBytes(StandardCharsets.UTF_8);
    private static final Duration SIGNATURE_STATUS_TIMEOUT = Duration.ofSeconds(90);
    private static final long SIGNATURE_STATUS_POLL_MILLIS = 1_500;
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final SolanaClient solanaClient;
    private final ExpenseContractRepository expenseContractRepository;
    private final ContractTransactionRepository contractTransactionRepository;

    @Value("${solana.program.id}")
    private String programId;

    /**
     * Informa el saldo de la wallet. Nunca falla hacia afuera.
     *
     * <p>Antes propagaba la excepcion, y como se llama al final de metodos
     * anotados con {@code @Transactional} —despues de guardar el contrato y su
     * transaccion—, un fallo aqui revertia esos guardados. El resultado era el
     * peor posible: la cuenta quedaba creada en Solana, que no se puede
     * deshacer, y la fila desaparecia de la base. Bastaba que el RPC de devnet
     * respondiera un 429 por limite de peticiones, cosa nada rara justo despues
     * de la rafaga de llamadas que hace el envio.</p>
     *
     * <p>Es un diagnostico: saber cuanto SOL queda importa porque si se agota
     * los despliegues empiezan a fallar. Pero un diagnostico no puede tener
     * autoridad para borrar datos de negocio, asi que se traga su propio error.</p>
     */
    private void obtenerSaldoBackend() {
        try {
            PublicKey walletKey = solanaClient.getSignerAccount().getPublicKey();
            long saldoEnLamports = solanaClient.getRpcClient().getApi().getBalance(walletKey);
            System.out.println("El saldo de la wallet es: " + (saldoEnLamports / 1_000_000_000.0) + " SOL");
        } catch (Exception exception) {
            System.out.println("No se pudo consultar el saldo de la wallet: " + exception.getMessage());
        }
    }

    @Override
    @Transactional
    public ContractAddress deployExpenseContract(Expense expense) throws Exception {
        // The expense contract is created once per expense. If this method is
        // retried after a successful deploy, the local database prevents
        // creating a duplicated on-chain account for the same business expense.
        expenseContractRepository.findByExpense(expense)
                .ifPresent(existingContract -> {
                    throw new IllegalArgumentException("Expense contract already exists for the given expense");
                });

        PublicKey programPublicKey = new PublicKey(programId);
        PublicKey authority = solanaClient.getSignerAccount().getPublicKey();
        PublicKey expensePda = expensePda(expense.getId(), programPublicKey);

        // La comprobacion local de arriba solo sabe lo que hay en esta base de
        // datos. Si la base se recreo, la PDA puede existir en la red aunque aqui
        // no haya registro, y enviar la creacion costaria una comision para
        // terminar rechazada.
        if (accountExistsOnChain(expensePda)) {
            return reconcileExistingExpenseContract(expense, expensePda);
        }

        String signature;
        try {
            // Solana transactions require a recent blockhash and explicit
            // instructions. The adapter builds the Anchor-compatible payload
            // here so the domain layer only depends on ExpenseSmartContractPort.
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
            if (isAlreadyInUse(exception)) {
                // Carrera entre la comprobacion y el envio: alguien creo la cuenta
                // en el intervalo. Se adopta la que ya existe en vez de reintentar.
                return reconcileExistingExpenseContract(expense, expensePda);
            }
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
        // Payment registration is idempotent from the backend perspective:
        // once a payment has a transaction hash, callers can safely retry and
        // receive the already persisted hash instead of sending a new tx.
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

        // Mismo caso que en el gasto: la PDA del pago puede existir en la red sin
        // que esta base tenga el registro.
        if (accountExistsOnChain(paymentPda)) {
            return reconcileExistingPaymentTransaction(expenseContractEntity, payment, paymentPda);
        }

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
            System.out.println("Payment account created on Solana. pda=" + paymentPda.toBase58()
                    + ", signature=" + signature);
            waitForSuccessfulSignature(signature);
        } catch (Exception exception) {
            System.out.println("Failed to register payment on Solana: " + exception.getMessage());
            if (isAlreadyInUse(exception)) {
                return reconcileExistingPaymentTransaction(expenseContractEntity, payment, paymentPda);
            }
            throw new RuntimeException("Failed to register payment on Solana: " + exception.getMessage(), exception);
        }

        TransactionHash transactionHash = new TransactionHash(signature);

        ContractTransaction transaction = new ContractTransaction();
        transaction.setContract(expenseContractEntity);
        transaction.setPayment(payment);
        transaction.setTransactionHash(transactionHash);
        transaction.setPaymentAddress(paymentPda.toBase58());
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

        // Actualizar una cuenta que no existe falla siempre. Antes se enviaba
        // igual y se pagaba la comision en cada intento; ahora se comprueba con
        // una lectura gratuita y se corta sin reintentar.
        if (!accountExistsOnChain(paymentPda)) {
            throw new PermanentContractSyncException(
                    "Payment account does not exist on-chain, nothing to update. paymentId="
                            + payment.getId() + ", pda=" + paymentPda.toBase58());
        }

        String signature;
        try {
            String latestBlockhash = latestBlockhash();
            Transaction transaction = new Transaction();
            transaction.addInstruction(updatePaymentInstruction(
                    programPublicKey,
                    authority,
                    expensePda,
                    paymentPda,
                    payment
            ));
            transaction.setRecentBlockHash(latestBlockhash);

            signature = sendTransaction(transaction, latestBlockhash);
            System.out.println("Payment updated on Solana. pda=" + paymentPda.toBase58()
                    + ", signature=" + signature);
            waitForSuccessfulSignature(signature);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to update payment status on Solana: " + exception.getMessage(), exception);
        }

        TransactionHash transactionHash = new TransactionHash(signature);

        ContractTransaction contractTransaction = new ContractTransaction();
        contractTransaction.setContract(expenseContractEntity);
        contractTransaction.setPayment(payment);
        contractTransaction.setTransactionHash(transactionHash);
        contractTransaction.setPaymentAddress(paymentPda.toBase58());
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
        // Anchor expects the first 8 bytes to be the instruction discriminator,
        // followed by the serialized arguments in little-endian order.
        var data = new AnchorData("create_expense")
                .u64(expense.getId())
                .u64(expense.getGroup().getId())
                .u64(expense.getUser().getId())
                .u64(toMinorUnits(expense.getAmount()))
                .i64(expense.getDueDate().atTime(LocalTime.MIDNIGHT).atZone(LIMA_ZONE).toEpochSecond())
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
            Payment payment
    ) throws Exception {
        var data = new AnchorData("update_payment")
                .u64(toMinorUnits(payment.getAmountPaid()))
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

    /**
     * Adopta un contrato de gasto que ya existe en la cadena.
     *
     * <p>Registra en la base local la PDA y, si se puede, la firma con la que se
     * creo. Eso cierra el hueco de no tener rastro local de transacciones
     * enviadas en ejecuciones anteriores.</p>
     */
    private ContractAddress reconcileExistingExpenseContract(Expense expense, PublicKey expensePda) {
        System.out.println("Expense account already exists on Solana, adopting it instead of creating. pda="
                + expensePda.toBase58());

        ExpenseContract expenseContract = expenseContractRepository.save(new ExpenseContract(
                new ContractAddress(expensePda.toBase58()),
                expense
        ));

        TransactionHash creationSignature = creationSignatureFor(expensePda);
        if (creationSignature != null) {
            ContractTransaction contractTransaction = new ContractTransaction();
            contractTransaction.setContract(expenseContract);
            contractTransaction.setTransactionHash(creationSignature);
            contractTransactionRepository.save(contractTransaction);
        }
        return expenseContract.getContractAddress();
    }

    /**
     * Adopta un pago que ya existe en la cadena, recuperando su firma de creacion.
     */
    private TransactionHash reconcileExistingPaymentTransaction(ExpenseContract expenseContract,
                                                                Payment payment,
                                                                PublicKey paymentPda) {
        System.out.println("Payment account already exists on Solana, adopting it instead of creating. pda="
                + paymentPda.toBase58());

        TransactionHash creationSignature = creationSignatureFor(paymentPda);
        if (creationSignature == null) {
            throw new PermanentContractSyncException(
                    "Payment account already exists on-chain but its signature could not be recovered. pda="
                            + paymentPda.toBase58());
        }

        ContractTransaction contractTransaction = new ContractTransaction();
        contractTransaction.setContract(expenseContract);
        contractTransaction.setPayment(payment);
        contractTransaction.setTransactionHash(creationSignature);
        contractTransaction.setPaymentAddress(paymentPda.toBase58());
        contractTransactionRepository.save(contractTransaction);

        return creationSignature;
    }

    /**
     * Consulta si una cuenta ya existe en la cadena.
     *
     * <p>Es una lectura: no se firma nada y no cuesta comisiones. Preguntar antes
     * de enviar una instruccion de creacion evita pagar por una transaccion que la
     * cadena va a rechazar de todas formas.</p>
     *
     * <p>Hace falta porque las PDA se derivan de identificadores de la base de
     * datos local. Una base recreada vuelve a numerar desde 1 y deriva las mismas
     * direcciones que ya existen en la red, asi que la base local no puede saber
     * por si sola lo que hay en la cadena.</p>
     */
    private boolean accountExistsOnChain(PublicKey account) {
        try {
            Map<String, Object> result = solanaClient.getRpcClient().call(
                    "getAccountInfo",
                    List.of(account.toBase58(), Map.of("encoding", "base64")),
                    Map.class
            );
            return result != null && result.get("value") != null;
        } catch (Exception exception) {
            // Si la consulta falla no se puede afirmar que la cuenta exista. Se
            // asume que no y se deja que el envio decida: perder una comision es
            // preferible a bloquear un registro legitimo por un error de red.
            System.out.println("Could not verify account on-chain, assuming it does not exist. account="
                    + account.toBase58() + ", message=" + exception.getMessage());
            return false;
        }
    }

    /**
     * Recupera la firma mas antigua asociada a una cuenta, que es la de su creacion.
     *
     * <p>Sirve para reconstruir en la base local el hash de una transaccion que se
     * envio en una ejecucion anterior. Tambien es una lectura sin costo.</p>
     */
    private TransactionHash creationSignatureFor(PublicKey account) {
        try {
            List<?> signatures = solanaClient.getRpcClient().call(
                    "getSignaturesForAddress",
                    List.of(account.toBase58(), Map.of("limit", 1000)),
                    List.class
            );
            if (signatures == null || signatures.isEmpty()) {
                return null;
            }
            // El RPC devuelve de la mas reciente a la mas antigua; la creacion es la ultima.
            Object oldest = signatures.get(signatures.size() - 1);
            if (oldest instanceof Map<?, ?> entry && entry.get("signature") instanceof String signature) {
                return new TransactionHash(signature);
            }
            return null;
        } catch (Exception exception) {
            System.out.println("Could not recover creation signature. account=" + account.toBase58()
                    + ", message=" + exception.getMessage());
            return null;
        }
    }

    /**
     * Reconoce los errores de la cadena que no tiene sentido reintentar.
     *
     * <p>Cuando una PDA ya existe, el programa Anchor rechaza la instruccion de
     * creacion y ese rechazo se repite identico en cada intento.</p>
     */
    private static boolean isAlreadyInUse(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("already in use")
                || normalized.contains("already initialized")
                || normalized.contains("custom program error: 0x0");
    }

    private String latestBlockhash() throws Exception {
        // solanaj does not expose every RPC helper used here, so this call uses
        // the generic RPC entry point and extracts the blockhash from the result.
        Map<String, Object> blockhashResult = solanaClient.getRpcClient().call(
                "getLatestBlockhash",
                null,
                Map.class
        );
        Map<String, Object> valueMap = (Map<String, Object>) blockhashResult.get("value");
        return (String) valueMap.get("blockhash");
    }

    private void waitForSuccessfulSignature(String signature) throws Exception {
        // sendTransaction returning a signature only means the cluster accepted
        // the transaction. Poll until it is confirmed/finalized so the database
        // record reflects an on-chain operation that really landed.
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
        // Expense PDA is deterministic: the same expense id always maps to the
        // same on-chain account for this program.
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
        // Store currency-like amounts as integer cents before sending them to
        // Solana to avoid decimal precision differences across runtimes.
        return amount
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    private static class AnchorData {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        AnchorData(String instructionName) throws Exception {
            // Anchor discriminators are sha256("global:<instruction>")[0..8].
            output.write(anchorDiscriminator(instructionName));
        }

        AnchorData u64(long value) {
            write(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());
            return this;
        }

        AnchorData i64(long value) {
            return u64(value);
        }

        AnchorData bool(boolean value) {
            output.write(value ? 1 : 0);
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

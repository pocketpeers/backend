package com.pocketpeers.backend.operations.infrastructure.blockchain.solana.adapters;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseChain;
import com.pocketpeers.backend.operations.domain.model.entities.ExpenseChainRecord;
import com.pocketpeers.backend.operations.domain.model.valueobjects.ContractAddress;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.model.valueobjects.TransactionHash;
import com.pocketpeers.backend.operations.domain.exceptions.PermanentContractSyncException;
import com.pocketpeers.backend.operations.domain.ports.out.ExpenseSmartContractPort;
import com.pocketpeers.backend.operations.infrastructure.blockchain.solana.services.SolanaClient;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseChainRecordRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseChainRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.Transaction;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.ComputeBudgetProgram;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.rpc.types.config.RpcSendTransactionConfig;
import org.p2p.solanaj.utils.Base58;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseSmartContractAdapter implements ExpenseSmartContractPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpenseSmartContractAdapter.class);

    // Semilla del PDA del gasto. Cambio junto con el layout de la cuenta: una
    // cuenta escrita con el formato viejo no se puede deserializar con el nuevo,
    // asi que los gastos anteriores se quedan donde estan, intactos y legibles
    // con el formato de entonces, y este programa no los toca.
    //
    // Ya no hay semilla de pago: los pagos no crean cuentas.
    private static final byte[] EXPENSE_SEED = "expense_v3".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_EXPENSE_NAME_BYTES = 64;
    private static final Duration SIGNATURE_STATUS_TIMEOUT = Duration.ofSeconds(90);
    private static final long SIGNATURE_STATUS_POLL_MILLIS = 1_500;
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    /**
     * Cada cuantos sondeos se vuelve a difundir la transaccion.
     *
     * <p>Con un sondeo cada 1,5 s, reenviar cada cuatro difunde cada seis
     * segundos. Mas seguido no ayuda —el nodo ya la tiene— y gasta cuota del
     * RPC; menos seguido deja huecos donde la transaccion puede caerse sin que
     * nadie la vuelva a ofrecer.</p>
     */
    private static final int REBROADCAST_EVERY_POLLS = 4;

    /**
     * Unidades de computo reservadas por transaccion.
     *
     * <p>Las instrucciones de este programa consumen alrededor de 30 000. El
     * valor por defecto que asume el planificador es 200 000 por instruccion,
     * asi que declarar 60 000 no recorta nada real y reduce lo que hay que
     * reservar, que es parte de lo que decide si la transaccion entra.</p>
     */
    private static final int COMPUTE_UNIT_LIMIT = 60_000;

    /** Longitud de una firma ed25519, en bytes. */
    private static final int SIGNATURE_BYTES = 64;

    private final SolanaClient solanaClient;
    private final ExpenseChainRepository expenseChainRepository;
    private final ExpenseChainRecordRepository expenseChainRecordRepository;
    private final PaymentRepository paymentRepository;

    @Value("${solana.program.id}")
    private String programId;

    /**
     * Precio por unidad de computo, en microlamports.
     *
     * <p>Es la comision de prioridad. Sin ella una transaccion compite en el
     * ultimo lugar de la cola del lider y, cuando la red va cargada, se cae sin
     * dejar rastro: el nodo la acepta, devuelve su firma y nadie la incluye.
     * Configurable porque el valor que hace falta depende de la congestion del
     * momento, y en devnet no es el mismo que en mainnet.</p>
     */
    @Value("${solana.priority-fee-micro-lamports:20000}")
    private int priorityFeeMicroLamports;

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
        expenseChainRepository.findByExpense(expense)
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
            return reconcileExistingExpenseChain(expense, expensePda);
        }

        String onChainName = truncateToMaxBytes(expense.getName());
        long dueDateUnix = dueDateUnix(expense);
        long amountMinorUnits = toMinorUnits(expense.getAmount());

        String signature;
        try {
            // Solana transactions require a recent blockhash and explicit
            // instructions. The adapter builds the Anchor-compatible payload
            // here so the domain layer only depends on ExpenseSmartContractPort.
            String latestBlockhash = latestBlockhash();
            Transaction transaction = new Transaction();
            computeBudgetInstructions().forEach(transaction::addInstruction);
            transaction.addInstruction(createExpenseInstruction(
                    programPublicKey, authority, expensePda, expense, onChainName, amountMinorUnits, dueDateUnix));

            // El aviso va despues de confirmar, no antes. Anunciar la creacion
            // al recibir la firma afirmaba algo que todavia no era cierto, y
            // cuando la transaccion se caia el log se contradecia a si mismo.
            signature = sendAndConfirm(transaction, latestBlockhash);
            LOGGER.info("Expense account created on Solana. pda={}, signature={}",
                    expensePda.toBase58(), signature);
        } catch (Exception exception) {
            LOGGER.warn("Failed to create expense on Solana: {}", exception.getMessage());
            if (isAlreadyInUse(exception)) {
                // Carrera entre la comprobacion y el envio: alguien creo la cuenta
                // en el intervalo. Se adopta la que ya existe en vez de reintentar.
                return reconcileExistingExpenseChain(expense, expensePda);
            }
            throw new RuntimeException("Failed to create expense on Solana: " + exception.getMessage(), exception);
        }

        // El mismo hash que acaba de calcular el programa. Se reproduce aqui en
        // vez de leerlo de la cadena porque el calculo es determinista y la
        // transaccion ya esta confirmada: una lectura mas no diria nada nuevo.
        String genesisHash = HexFormat.of().formatHex(genesisHash(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getUser().getId(),
                amountMinorUnits,
                dueDateUnix,
                onChainName
        ));

        ExpenseChain expenseChain = expenseChainRepository.save(new ExpenseChain(
                new ContractAddress(expensePda.toBase58()),
                expense,
                genesisHash
        ));

        ExpenseChainRecord transaction = new ExpenseChainRecord();
        transaction.setChain(expenseChain);
        transaction.setTransactionHash(new TransactionHash(signature));
        transaction.setChainHash(genesisHash);
        transaction.setRecordIndex(0);
        expenseChainRecordRepository.save(transaction);

        obtenerSaldoBackend();

        return expenseChain.getContractAddress();
    }

    /**
     * Añade un eslabon a la cadena del gasto con el estado actual de un pago.
     *
     * <p>Antes habia dos metodos, uno para registrar y otro para actualizar,
     * porque cada pago tenia su propia cuenta en la cadena. Ya no la tiene, y
     * con ella desaparecio la distincion: corregir un pago es escribir un
     * movimiento mas, igual que registrarlo por primera vez.</p>
     *
     * <p>Eso obliga a resolver la idempotencia de otra forma. Antes bastaba
     * preguntar si la cuenta del pago existia; ahora no hay cuenta que
     * preguntar, y reenviar un movimiento que ya entro añadiria un eslabon
     * duplicado que corromperia la cadena en silencio. La comprobacion es
     * comparar el {@code chain_hash} de la cadena con el que este backend cree
     * que deberia haber: si coinciden, vamos al dia; si el de la cadena es el
     * eslabon que ibamos a escribir, la transaccion si entro y lo que se perdio
     * fue el registro local.</p>
     */
    @Override
    @Transactional
    public TransactionHash recordPayment(Expense expense, Payment payment) throws Exception {
        ExpenseChain expenseChainEntity = expenseChainRepository.findByExpense(expense)
                .orElseThrow(() -> new RuntimeException("Expense contract not found for the given expense"));

        PublicKey programPublicKey = new PublicKey(programId);
        PublicKey authority = solanaClient.getSignerAccount().getPublicKey();
        PublicKey expensePda = new PublicKey(expenseChainEntity.getContractAddress().address());

        // Actualizar una cuenta que no existe falla siempre. Antes se enviaba
        // igual y se pagaba la comision en cada intento; ahora se comprueba con
        // una lectura gratuita y se corta sin reintentar.
        ExpenseAccountState onChain = readExpenseAccount(expensePda);
        if (onChain == null) {
            throw new PermanentContractSyncException(
                    "Expense account does not exist on-chain, nothing to record. expenseId="
                            + expense.getId() + ", pda=" + expensePda.toBase58());
        }
        if (!onChain.active()) {
            throw new PermanentContractSyncException(
                    "Expense is cancelled on-chain, it no longer accepts payments. expenseId=" + expense.getId());
        }

        // El programa guarda el total pagado del gasto entero, no el de cada
        // pago, asi que hay que sumarlo aqui. Se consulta a la base en vez de
        // recorrer expense.getPayments() porque este metodo corre en un
        // manejador asincrono y esa coleccion puede venir de una entidad ya
        // separada de la sesion, con importes viejos.
        long expensePaidMinorUnits = totalPaidMinorUnits(expense.getId());
        long paymentPaidMinorUnits = toMinorUnits(payment.getAmountPaid());
        PaymentStatus status = PaymentStatus.valueOf(payment.getStatus());
        boolean confirmed = Boolean.TRUE.equals(payment.getConfirmed());

        byte[] lineHash = paymentLineHash(
                payment.getId(),
                payment.getUser().getId(),
                paymentPaidMinorUnits,
                expensePaidMinorUnits,
                statusByte(status),
                confirmed
        );

        String expectedPrevious = expenseChainEntity.getLastChainHash() != null
                ? expenseChainEntity.getLastChainHash()
                : expenseChainEntity.getGenesisHash();
        String onChainHash = HexFormat.of().formatHex(onChain.chainHash());
        String nextHash = HexFormat.of().formatHex(link(onChain.chainHash(), lineHash));

        // El caso de recuperacion: la transaccion anterior entro en la cadena
        // pero su fila no llego a guardarse. Se detecta porque la cadena esta
        // exactamente un eslabon por delante de lo que tenemos anotado, y ese
        // eslabon es el de este mismo movimiento.
        var alreadyRecorded = expenseChainRecordRepository
                .findFirstByPayment_IdAndPaymentLineHash(payment.getId(), HexFormat.of().formatHex(lineHash));
        if (alreadyRecorded.isPresent() && onChainHash.equals(alreadyRecorded.get().getChainHash())) {
            return alreadyRecorded.get().getTransactionHash();
        }
        if (expectedPrevious != null && !onChainHash.equals(expectedPrevious)) {
            TransactionHash recovered = recoverLostRecord(
                    expenseChainEntity, payment, expensePda, onChain, onChainHash, lineHash, expectedPrevious);
            if (recovered != null) {
                return recovered;
            }
        }

        // El programa rechaza que el total pagado retroceda. Detectarlo aqui
        // con una lectura gratuita evita pagar la comision de una transaccion
        // que la cadena va a rechazar igual.
        if (expensePaidMinorUnits < onChain.paidMinorUnits()) {
            throw new PermanentContractSyncException(
                    "Local paid total is behind the chain, refusing to write. expenseId=" + expense.getId()
                            + ", local=" + expensePaidMinorUnits + ", onChain=" + onChain.paidMinorUnits());
        }
        if (expensePaidMinorUnits > toMinorUnits(expense.getAmount())) {
            throw new PermanentContractSyncException(
                    "Paid total exceeds the expense amount, refusing to write. expenseId=" + expense.getId());
        }

        String signature;
        try {
            String latestBlockhash = latestBlockhash();
            Transaction transaction = new Transaction();
            computeBudgetInstructions().forEach(transaction::addInstruction);
            transaction.addInstruction(recordPaymentInstruction(
                    programPublicKey,
                    authority,
                    expensePda,
                    payment,
                    paymentPaidMinorUnits,
                    expensePaidMinorUnits,
                    status,
                    confirmed
            ));

            signature = sendAndConfirm(transaction, latestBlockhash);
            LOGGER.info("Payment recorded on Solana. pda={}, paymentId={}, signature={}",
                    expensePda.toBase58(), payment.getId(), signature);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to record payment on Solana: " + exception.getMessage(), exception);
        }

        TransactionHash transactionHash = new TransactionHash(signature);
        persistRecord(expenseChainEntity, payment, transactionHash, lineHash, nextHash,
                onChain.recordsCount() + 1);

        obtenerSaldoBackend();

        return transactionHash;
    }

    /**
     * Adopta un movimiento que entro en la cadena pero cuya fila se perdio.
     *
     * <p>Solo se acepta si el {@code chain_hash} de la cadena es exactamente el
     * eslabon que resulta de aplicar este movimiento sobre lo ultimo que
     * teniamos anotado. Cualquier otra diferencia significa que la cadena y la
     * base divergieron por un motivo que no sabemos, y escribir encima a ciegas
     * empeoraria las cosas.</p>
     */
    private TransactionHash recoverLostRecord(ExpenseChain expenseChain,
                                              Payment payment,
                                              PublicKey expensePda,
                                              ExpenseAccountState onChain,
                                              String onChainHash,
                                              byte[] lineHash,
                                              String expectedPrevious) {
        byte[] previousBytes = HexFormat.of().parseHex(expectedPrevious);
        String wouldBe = HexFormat.of().formatHex(link(previousBytes, lineHash));
        if (!onChainHash.equals(wouldBe)) {
            throw new PermanentContractSyncException(
                    "Chain and database diverged, manual reconciliation needed. expenseId="
                            + expenseChain.getExpense().getId()
                            + ", onChain=" + onChainHash + ", expected=" + expectedPrevious);
        }

        System.out.println("Payment was already recorded on-chain, adopting it instead of writing again. paymentId="
                + payment.getId());

        TransactionHash signature = latestSignatureFor(expensePda);
        if (signature == null) {
            throw new PermanentContractSyncException(
                    "Payment already recorded on-chain but its signature could not be recovered. paymentId="
                            + payment.getId());
        }
        persistRecord(expenseChain, payment, signature, lineHash, onChainHash, onChain.recordsCount());
        return signature;
    }

    private void persistRecord(ExpenseChain expenseChain,
                               Payment payment,
                               TransactionHash transactionHash,
                               byte[] lineHash,
                               String chainHash,
                               int recordIndex) {
        ExpenseChainRecord chainRecord = new ExpenseChainRecord();
        chainRecord.setChain(expenseChain);
        chainRecord.setPayment(payment);
        chainRecord.setTransactionHash(transactionHash);
        chainRecord.setPaymentLineHash(HexFormat.of().formatHex(lineHash));
        chainRecord.setChainHash(chainHash);
        chainRecord.setRecordIndex(recordIndex);
        expenseChainRecordRepository.save(chainRecord);

        // El avance de la cadena se anota solo despues de confirmar. Guardarlo
        // antes dejaria la base por delante y nada volveria a cuadrar.
        expenseChain.setLastChainHash(chainHash);
        expenseChain.setRecordsCount(recordIndex);
        expenseChainRepository.save(expenseChain);
    }

    private TransactionInstruction createExpenseInstruction(
            PublicKey programPublicKey,
            PublicKey authority,
            PublicKey expensePda,
            Expense expense,
            String onChainName,
            long amountMinorUnits,
            long dueDateUnix
    ) throws Exception {
        // Anchor expects the first 8 bytes to be the instruction discriminator,
        // followed by the serialized arguments in little-endian order.
        var data = new AnchorData("create_expense")
                .u64(expense.getId())
                .u64(expense.getGroup().getId())
                .u64(expense.getUser().getId())
                .u64(amountMinorUnits)
                .i64(dueDateUnix)
                .string(onChainName)
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

    /**
     * Sin cuenta de pago y sin {@code system_program}: esta instruccion no crea
     * nada, solo modifica la cuenta del gasto. Por eso cuesta la comision y
     * nada mas.
     */
    private TransactionInstruction recordPaymentInstruction(
            PublicKey programPublicKey,
            PublicKey authority,
            PublicKey expensePda,
            Payment payment,
            long paymentPaidMinorUnits,
            long expensePaidMinorUnits,
            PaymentStatus status,
            boolean confirmed
    ) throws Exception {
        var data = new AnchorData("record_payment")
                .u64(payment.getId())
                .u64(payment.getUser().getId())
                .u64(paymentPaidMinorUnits)
                .u64(expensePaidMinorUnits)
                .u8(statusByte(status))
                .bool(confirmed)
                .toByteArray();

        return new TransactionInstruction(
                programPublicKey,
                List.of(
                        new AccountMeta(expensePda, false, true),
                        new AccountMeta(authority, true, false)
                ),
                data
        );
    }

    /**
     * Firma, envia y espera la confirmacion, difundiendo mientras espera.
     *
     * <p>Reemplaza al par enviar-y-sondear anterior, que perdia transacciones en
     * silencio. Que {@code sendTransaction} devuelva una firma solo significa
     * que el nodo acepto el envio; no que la transaccion vaya a entrar en un
     * bloque. Si el lider la descarta —por congestion, o porque llego sin
     * comision de prioridad— nadie la vuelve a ofrecer, el sondeo agota sus
     * noventa segundos y el gasto se queda sin anclar. Ese fue exactamente el
     * caso del gasto 1: firma emitida, cuenta nunca creada, la red sin noticia
     * de la transaccion.</p>
     *
     * <p>En Solana la difusion es responsabilidad del cliente. La transaccion se
     * firma una vez y se reenvian <b>los mismos bytes</b> cada pocos segundos
     * hasta que confirme o hasta que el blockhash caduque. Reenviar no duplica
     * nada: la firma es la identidad de la transaccion, asi que la red descarta
     * las copias de una que ya entro.</p>
     *
     * <p>El corte lo marca {@code isBlockhashValid}, no el reloj. Mientras el
     * blockhash siga vivo la transaccion todavia puede entrar; cuando caduca ya
     * no puede, y seguir esperando solo retrasa el reintento.</p>
     */
    private String sendAndConfirm(Transaction transaction, String latestBlockhash) throws Exception {
        transaction.setRecentBlockHash(latestBlockhash);
        transaction.sign(List.of(solanaClient.getSignerAccount()));

        byte[] rawTransaction = transaction.serialize();
        String encodedTransaction = Base64.getEncoder().encodeToString(rawTransaction);
        String signature = signatureOf(rawTransaction);

        var api = solanaClient.getRpcClient().getApi();

        // El primer envio va con verificacion previa para que un error real del
        // programa —cuenta ya creada, saldo insuficiente, datos invalidos— se
        // sepa de inmediato y no despues de noventa segundos de espera.
        api.sendRawTransaction(encodedTransaction, sendConfig(false));

        long deadline = System.currentTimeMillis() + SIGNATURE_STATUS_TIMEOUT.toMillis();
        int poll = 0;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(SIGNATURE_STATUS_POLL_MILLIS);
            poll++;

            if (isSignatureConfirmed(signature)) {
                return signature;
            }

            // Las difusiones posteriores saltan la verificacion previa: ya se
            // hizo en el primer envio y repetirla en cada reenvio solo gasta
            // cuota del RPC.
            if (poll % REBROADCAST_EVERY_POLLS == 0) {
                if (!api.isBlockhashValid(latestBlockhash)) {
                    break;
                }
                api.sendRawTransaction(encodedTransaction, sendConfig(true));
            }
        }

        // Una ultima comprobacion: la transaccion pudo entrar entre el ultimo
        // sondeo y la salida del bucle.
        if (isSignatureConfirmed(signature)) {
            return signature;
        }

        throw new RuntimeException(
                "La transaccion no llego a entrar en la cadena antes de que caducara el blockhash. signature="
                        + signature);
    }

    /**
     * Instrucciones de presupuesto de computo, primeras de cada transaccion.
     *
     * <p>Van siempre juntas. El limite sin el precio no prioriza nada, y el
     * precio sin el limite se multiplica por el valor por defecto, que es tres
     * veces lo que estas instrucciones consumen: se pagaria de mas por la misma
     * prioridad.</p>
     */
    private List<TransactionInstruction> computeBudgetInstructions() {
        return List.of(
                ComputeBudgetProgram.setComputeUnitLimit(COMPUTE_UNIT_LIMIT),
                ComputeBudgetProgram.setComputeUnitPrice(priorityFeeMicroLamports)
        );
    }

    private RpcSendTransactionConfig sendConfig(boolean skipPreflight) {
        return RpcSendTransactionConfig.builder()
                .encoding(RpcSendTransactionConfig.Encoding.base64)
                .skipPreflight(skipPreflight)
                // Que el propio nodo reenvie tambien, ademas de la difusion de
                // este lado. Las dos cosas suman; ninguna sustituye a la otra.
                .maxRetries(5)
                .preflightCommitment("confirmed")
                .build();
    }

    /**
     * La firma de una transaccion serializada.
     *
     * <p>El formato empieza con la cantidad de firmas en compact-u16 —un solo
     * byte mientras sean menos de 128, que siempre es el caso aqui— y sigue con
     * las firmas. La primera es la de la transaccion.</p>
     */
    private static String signatureOf(byte[] rawTransaction) {
        return Base58.encode(java.util.Arrays.copyOfRange(rawTransaction, 1, 1 + SIGNATURE_BYTES));
    }

    private boolean isSignatureConfirmed(String signature) throws Exception {
        Map<String, Object> result = solanaClient.getRpcClient().call(
                "getSignatureStatuses",
                signatureStatusParams(signature),
                Map.class
        );
        List<?> values = (List<?>) result.get("value");
        if (values == null || values.isEmpty() || !(values.get(0) instanceof Map<?, ?> status)) {
            return false;
        }
        Object error = status.get("err");
        if (error != null) {
            throw new RuntimeException("Solana transaction failed. signature=" + signature + ", err=" + error);
        }
        Object confirmationStatus = status.get("confirmationStatus");
        return "confirmed".equals(confirmationStatus) || "finalized".equals(confirmationStatus);
    }

    /**
     * Adopta un contrato de gasto que ya existe en la cadena.
     *
     * <p>Registra en la base local la PDA y, si se puede, la firma con la que se
     * creo. Eso cierra el hueco de no tener rastro local de transacciones
     * enviadas en ejecuciones anteriores.</p>
     *
     * <p>El estado del encadenado se toma de la cuenta, no se recalcula: si el
     * gasto ya recibio pagos, el {@code chain_hash} de la cadena es el bueno y
     * el hash de creacion por si solo se habria quedado corto.</p>
     */
    private ContractAddress reconcileExistingExpenseChain(Expense expense, PublicKey expensePda) {
        System.out.println("Expense account already exists on Solana, adopting it instead of creating. pda="
                + expensePda.toBase58());

        ExpenseChain expenseChain = new ExpenseChain(
                new ContractAddress(expensePda.toBase58()),
                expense
        );

        ExpenseAccountState onChain = readExpenseAccount(expensePda);
        if (onChain != null) {
            String chainHash = HexFormat.of().formatHex(onChain.chainHash());
            expenseChain.setLastChainHash(chainHash);
            expenseChain.setRecordsCount(onChain.recordsCount());
            if (onChain.recordsCount() == 0) {
                // Sin movimientos todavia, el encadenado sigue siendo el de creacion.
                expenseChain.setGenesisHash(chainHash);
            }
        }
        expenseChain = expenseChainRepository.save(expenseChain);

        TransactionHash creationSignature = creationSignatureFor(expensePda);
        if (creationSignature != null) {
            ExpenseChainRecord chainRecord = new ExpenseChainRecord();
            chainRecord.setChain(expenseChain);
            chainRecord.setTransactionHash(creationSignature);
            chainRecord.setChainHash(expenseChain.getGenesisHash());
            chainRecord.setRecordIndex(0);
            expenseChainRecordRepository.save(chainRecord);
        }
        return expenseChain.getContractAddress();
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
                    List.of(account.toBase58(), accountInfoConfig()),
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
     * Lee y deserializa la cuenta del gasto.
     *
     * <p>El layout lo fija el programa: 8 bytes de discriminador de Anchor y
     * despues los campos en el orden en que estan declarados. El nombre es una
     * cadena Borsh —4 bytes de longitud y luego los bytes—, asi que todo lo que
     * viene detras esta desplazado por esa longitud y hay que leerla antes.</p>
     *
     * <p>Devuelve {@code null} solo cuando la cadena responde y dice que la
     * cuenta no esta. Si la lectura falla —un 429, un corte de red— lanza una
     * excepcion normal para que el manejador reintente.</p>
     *
     * <p>La distincion importa mas de lo que parece. Antes las dos cosas
     * devolvian {@code null} y quien llamaba las trataba igual: como cuenta
     * inexistente, que es una falla permanente y no se reintenta. Con eso, un
     * limite de peticiones pasajero dejaba el pago sin anclar para siempre.</p>
     */
    @SuppressWarnings("unchecked")
    private ExpenseAccountState readExpenseAccount(PublicKey account) {
        Map<String, Object> result;
        try {
            result = solanaClient.getRpcClient().call(
                    "getAccountInfo",
                    List.of(account.toBase58(), accountInfoConfig()),
                    Map.class
            );
        } catch (Exception exception) {
            throw new RuntimeException("Could not read expense account on-chain. account="
                    + account.toBase58() + ", message=" + exception.getMessage(), exception);
        }

        if (result == null || result.get("value") == null) {
            return null;
        }
        Map<String, Object> value = (Map<String, Object>) result.get("value");
        List<String> data = (List<String>) value.get("data");
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            byte[] raw = Base64.getDecoder().decode(data.get(0));

            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            // Layout fijo de 103 bytes, sin campos de longitud variable: el
            // nombre del gasto ya no se guarda en la cuenta, solo se hashea. Por
            // eso aqui basta un salto constante y no hay que leer un prefijo de
            // longitud para saber donde empieza el resto.
            //
            //   0  discriminador (8)   40  backend_expense_id (8)
            //   8  authority (32)      48  amount_minor_units (8)
            //  56  paid_minor_units (8)
            //  64  chain_hash (32)     96  records_count (4)
            // 100  active (1)         101  settled (1)     102  bump (1)
            buffer.position(8 + 32 + 8 + 8); // discriminador, authority, id de backend e importe

            long paidMinorUnits = buffer.getLong();
            byte[] chainHash = new byte[32];
            buffer.get(chainHash);
            int recordsCount = buffer.getInt();
            boolean active = buffer.get() != 0;
            boolean settled = buffer.get() != 0;

            return new ExpenseAccountState(active, settled, chainHash, recordsCount, paidMinorUnits);
        } catch (Exception exception) {
            // La cuenta existe pero no tiene la forma que espera este codigo.
            // Reintentar no va a cambiar los bytes: o es una cuenta de otro
            // programa, o el layout del contrato cambio sin actualizar esto.
            throw new PermanentContractSyncException("Expense account has an unexpected layout. account="
                    + account.toBase58() + ", message=" + exception.getMessage());
        }
    }

    /**
     * Configuracion de las lecturas de cuenta.
     *
     * <p>El {@code commitment} es lo importante. Sin el, {@code getAccountInfo}
     * usa {@code finalized}, que va una decena de segundos por detras, mientras
     * que el envio solo espera a {@code confirmed}. El resultado era que una
     * cuenta recien creada se leia como inexistente durante ese hueco: el gasto
     * se anclaba bien y sus pagos se abandonaban acto seguido porque, segun la
     * lectura, el gasto no estaba. Leer al mismo nivel al que se espera cierra
     * ese hueco.</p>
     */
    private static Map<String, String> accountInfoConfig() {
        return Map.of("encoding", "base64", "commitment", "confirmed");
    }

    /**
     * Recupera la firma mas antigua asociada a una cuenta, que es la de su creacion.
     *
     * <p>Sirve para reconstruir en la base local el hash de una transaccion que se
     * envio en una ejecucion anterior. Tambien es una lectura sin costo.</p>
     */
    private TransactionHash creationSignatureFor(PublicKey account) {
        List<?> signatures = signaturesFor(account);
        if (signatures == null || signatures.isEmpty()) {
            return null;
        }
        // El RPC devuelve de la mas reciente a la mas antigua; la creacion es la ultima.
        return signatureAt(signatures, signatures.size() - 1);
    }

    /** La firma mas reciente de la cuenta: el ultimo movimiento que entro. */
    private TransactionHash latestSignatureFor(PublicKey account) {
        List<?> signatures = signaturesFor(account);
        if (signatures == null || signatures.isEmpty()) {
            return null;
        }
        return signatureAt(signatures, 0);
    }

    /**
     * Firmas de una cuenta, de la mas reciente a la mas antigua.
     *
     * <p>El {@code commitment} tiene que ser {@code confirmed} por lo mismo que
     * en las lecturas de cuenta: por defecto seria {@code finalized}, que va por
     * detras. Al adoptar un movimiento recien escrito, la firma que se busca
     * todavia no esta finalizada y la lista devolvia la anterior —normalmente la
     * de la creacion del gasto— que se guardaba como si fuera la del pago. El
     * hash que enseñaba la app apuntaba entonces a la transaccion equivocada.</p>
     */
    private List<?> signaturesFor(PublicKey account) {
        try {
            return solanaClient.getRpcClient().call(
                    "getSignaturesForAddress",
                    List.of(account.toBase58(), Map.of("limit", 1000, "commitment", "confirmed")),
                    List.class
            );
        } catch (Exception exception) {
            System.out.println("Could not recover signatures. account=" + account.toBase58()
                    + ", message=" + exception.getMessage());
            return null;
        }
    }

    private TransactionHash signatureAt(List<?> signatures, int index) {
        Object entry = signatures.get(index);
        if (entry instanceof Map<?, ?> map && map.get("signature") instanceof String signature) {
            return new TransactionHash(signature);
        }
        return null;
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
        @SuppressWarnings("unchecked")
        Map<String, Object> valueMap = (Map<String, Object>) blockhashResult.get("value");
        return (String) valueMap.get("blockhash");
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

    private long dueDateUnix(Expense expense) {
        return expense.getDueDate().atTime(LocalTime.MIDNIGHT).atZone(LIMA_ZONE).toEpochSecond();
    }

    /**
     * Suma lo pagado en todos los pagos del gasto.
     *
     * <p>El programa guarda el acumulado del gasto, no el de cada pago, porque
     * es lo unico que necesita para saber si quedo liquidado sin que nadie le
     * diga de antemano cuantos pagos va a haber.</p>
     */
    private long totalPaidMinorUnits(Long expenseId) {
        BigDecimal total = paymentRepository.findAllByExpenseId(expenseId).stream()
                .map(Payment::getAmountPaid)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return toMinorUnits(total);
    }

    /**
     * Recorta el nombre a lo que acepta el programa.
     *
     * <p>El limite son 64 bytes, no 64 caracteres: una tilde ocupa dos. Cortar
     * por bytes a secas partiria el ultimo caracter multibyte por la mitad y el
     * programa rechazaria la cadena, asi que se retrocede hasta un limite de
     * caracter.</p>
     */
    private String truncateToMaxBytes(String name) {
        String safe = name == null ? "" : name.trim();
        byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_EXPENSE_NAME_BYTES) {
            return safe;
        }
        int end = MAX_EXPENSE_NAME_BYTES;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
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

    /**
     * El byte con el que viaja el estado en el encadenado.
     *
     * <p>Es explicito y no el ordinal del enum: reordenar las constantes algun
     * dia cambiaria en silencio todos los hashes ya escritos. El programa usa
     * exactamente estos mismos valores.</p>
     */
    private static int statusByte(PaymentStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case PARTIAL -> 1;
            case COMPLETED -> 2;
        };
    }

    // --- Encadenado -------------------------------------------------------
    //
    // Estas tres funciones reproducen byte a byte lo que hace el programa. No
    // se mandan los hashes ya calculados en la instruccion justamente para no
    // tener dos definiciones canonicas que puedan separarse; aqui se recalculan
    // solo para poder guardarlos y comparar, y si alguna vez dejaran de
    // coincidir, la comparacion contra la cadena lo delata en el acto.

    private static byte[] genesisHash(long backendExpenseId,
                                      long groupId,
                                      long creatorUserId,
                                      long amountMinorUnits,
                                      long dueDateUnix,
                                      String name) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(leBytes(backendExpenseId));
        digest.update(leBytes(groupId));
        digest.update(leBytes(creatorUserId));
        digest.update(leBytes(amountMinorUnits));
        digest.update(leBytes(dueDateUnix));
        digest.update(name.getBytes(StandardCharsets.UTF_8));
        return digest.digest();
    }

    private static byte[] paymentLineHash(long backendPaymentId,
                                          long payerUserId,
                                          long paymentPaidMinorUnits,
                                          long expensePaidMinorUnits,
                                          int statusByte,
                                          boolean confirmed) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(leBytes(backendPaymentId));
        digest.update(leBytes(payerUserId));
        digest.update(leBytes(paymentPaidMinorUnits));
        digest.update(leBytes(expensePaidMinorUnits));
        digest.update((byte) statusByte);
        digest.update((byte) (confirmed ? 1 : 0));
        return digest.digest();
    }

    private static byte[] link(byte[] previousChainHash, byte[] lineHash) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(previousChainHash);
            digest.update(lineHash);
            return digest.digest();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static byte[] leBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }

    /** Lo que hace falta de la cuenta del gasto para decidir si escribir o no. */
    private record ExpenseAccountState(
            boolean active,
            boolean settled,
            byte[] chainHash,
            int recordsCount,
            long paidMinorUnits
    ) {}

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

        AnchorData u8(int value) {
            output.write(value);
            return this;
        }

        AnchorData bool(boolean value) {
            output.write(value ? 1 : 0);
            return this;
        }

        /**
         * Cadena en formato Borsh: longitud en 4 bytes little-endian y despues
         * los bytes UTF-8. La longitud va en bytes, no en caracteres.
         */
        AnchorData string(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
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

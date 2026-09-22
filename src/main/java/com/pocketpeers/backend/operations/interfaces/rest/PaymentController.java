package com.pocketpeers.backend.operations.interfaces.rest;

import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.commands.ConfirmPaymentCommand;
import com.pocketpeers.backend.operations.domain.model.queries.*;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.services.PaymentCommandService;
import com.pocketpeers.backend.operations.domain.services.PaymentQueryService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseChainRecordRepository;
import com.pocketpeers.backend.operations.interfaces.rest.resources.MakePaymentResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreatePaymentResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.PaymentResource;
import com.pocketpeers.backend.operations.interfaces.rest.transform.MakePaymentCommandFromResourceAssembler;
import com.pocketpeers.backend.operations.interfaces.rest.transform.CreatePaymentCommandFromResourceAssembler;
import com.pocketpeers.backend.operations.interfaces.rest.transform.PaymentResourceFromEntityAssembler;
import com.pocketpeers.backend.shared.interfaces.rest.resources.MessageResource;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name="Payments", description = "Payments Management Endpoint")
public class PaymentController {
    private final PaymentQueryService paymentQueryService;
    private final PaymentCommandService paymentCommandService;
    private final UserRepository userRepository;
    private final ExpenseChainRecordRepository expenseChainRecordRepository;

    public PaymentController(PaymentQueryService paymentQueryService, PaymentCommandService paymentCommandService,
                             UserRepository userRepository,
                             ExpenseChainRecordRepository expenseChainRecordRepository) {
        this.paymentQueryService = paymentQueryService;
        this.paymentCommandService = paymentCommandService;
        this.userRepository = userRepository;
        this.expenseChainRecordRepository = expenseChainRecordRepository;
    }

    @PostMapping
    public ResponseEntity<PaymentResource> createPayment(@RequestBody CreatePaymentResource resource) {
        var command = CreatePaymentCommandFromResourceAssembler.toCommandFromResource(resource);
        var paymentId = paymentCommandService.handle(command);
        System.out.println("Payment ID: " + paymentId);
        var getPaymentById = new GetPaymentByIdQuery(paymentId);
        var payment = paymentQueryService.handle(getPaymentById);
        var paymentResource = toPaymentResource(payment.get());
        return new ResponseEntity<>(paymentResource, HttpStatus.CREATED);
    }

    @PostMapping("{paymentId}/pay")
    public ResponseEntity<MessageResource> makePayment(@PathVariable Long paymentId, @RequestBody MakePaymentResource resource) {
        var makePaymentCommand = MakePaymentCommandFromResourceAssembler.toCommandFromResource(resource, paymentId);
        paymentCommandService.handle(makePaymentCommand);
        return ResponseEntity.ok(new MessageResource("Updated payment with ID: " + paymentId));
    }

    @PostMapping("/{paymentId}/confirm")
    public ResponseEntity<MessageResource> confirmPayment(@PathVariable Long paymentId, Authentication authentication) {
        var confirmPaymentCommand = new ConfirmPaymentCommand(paymentId, authentication.getName());
        paymentCommandService.handle(confirmPaymentCommand);
        return ResponseEntity.ok(new MessageResource("Confirmed payment with ID: " + paymentId));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResource>> getAllPayments() {
        var getAllPaymentsQuery = new GetAllPaymentsQuery();
        var payments = paymentQueryService.handle(getAllPaymentsQuery);
        var paymentResources = payments.stream().map(this::toPaymentResource).toList();
        return ResponseEntity.ok(paymentResources);
    }

    @GetMapping("/userId/{userId}")
    public ResponseEntity<List<PaymentResource>> getPaymentByUserId(@PathVariable Long userId) {
        var getAllPaymentsByUserIdQuery = new GetAllPaymentsByUserIdQuery(userId);
        var payments = paymentQueryService.handle(getAllPaymentsByUserIdQuery);
        var paymentResources = payments.stream().map(this::toPaymentResource).toList();
        return ResponseEntity.ok(paymentResources);
    }

    @GetMapping("/expenseId/{expenseId}")
    public ResponseEntity<List<PaymentResource>> getPaymentByExpenseId(@PathVariable Long expenseId) {
        var getAllPaymentsByExpenseIdQuery = new GetAllPaymentsByExpenseIdQuery(expenseId);
        var payments = paymentQueryService.handle(getAllPaymentsByExpenseIdQuery);
        var paymentResources = payments.stream().map(this::toPaymentResource).toList();
        return ResponseEntity.ok(paymentResources);
    }

    /**
     * Todos los pagos de un grupo, de un viaje.
     *
     * <p>El resumen de grupo los pedia gasto por gasto y de forma secuencial:
     * un grupo con veinte gastos hacia veinte peticiones encadenadas antes de
     * poder dibujar el grafico. Con esta, el cliente hace una.</p>
     */
    @GetMapping("/group/{groupId}")
    @Operation(summary = "Get all payments of a group")
    public ResponseEntity<List<PaymentResource>> getPaymentsByGroupId(@PathVariable Long groupId) {
        var query = new GetAllPaymentsByGroupIdQuery(groupId);
        var payments = paymentQueryService.handle(query);
        var paymentResources = payments.stream().map(this::toPaymentResource).toList();
        return ResponseEntity.ok(paymentResources);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResource> getPaymentById(@PathVariable Long paymentId, Authentication authentication) {
        var getPaymentByIdQuery = new GetPaymentByIdQuery(paymentId);
        var payment = paymentQueryService.handle(getPaymentByIdQuery);
        var record = expenseChainRecordRepository
                .findFirstByPayment_IdOrderByRecordIndexDesc(payment.get().getId());
        var paymentResource = PaymentResourceFromEntityAssembler.toResourceFromEntity(
                payment.get(),
                canViewEvidence(payment.get(), authentication),
                record.map(chainRecord -> chainRecord.getTransactionHash().hash()).orElse(""),
                record.map(chainRecord -> chainRecord.getCreatedAt()).orElse(null)
        );
        return ResponseEntity.ok(paymentResource);
    }

    @GetMapping("/userId/{userId}/status/{status}")
    public ResponseEntity<List<PaymentResource>> getPaymentByGroupIdAndUserIdAndStatus(@PathVariable Long userId, @PathVariable PaymentStatus status) {
        var getAllPaymentsByUserIdAndStatusQuery = new GetAllPaymentsByUserIdAndStatusQuery(userId, status);
        var payments = paymentQueryService.handle(getAllPaymentsByUserIdAndStatusQuery);
        var paymentResources = payments.stream().map(this::toPaymentResource).toList();
        return ResponseEntity.ok(paymentResources);
    }

    @GetMapping("/incoming/{userId}")
    public ResponseEntity<List<PaymentResource>> getIncomingPaymentsByUserId(@PathVariable Long userId) {
        var getIncomingPaymentsByUserIdQuery = new GetIncomingPaymentsByUserIdQuery(userId);
        var payments = paymentQueryService.handle(getIncomingPaymentsByUserIdQuery);
        var paymentResources = payments.stream().map(this::toPaymentResource).toList();
        return ResponseEntity.ok(paymentResources);
    }

    /**
     * Quien puede ver la evidencia de un pago: las dos partes, y nadie mas.
     *
     * <p>El deudor, porque es suya, y el acreedor, porque tiene que revisarla
     * antes de confirmar el pago. Son los dos que participan en esa obligacion
     * concreta.</p>
     *
     * <p>Antes tambien la veia el administrador del grupo, cualquiera que fuera
     * el pago. Eso le daba acceso a las fotos de comprobante de todos sus
     * integrantes —transferencias, numeros de cuenta, montos ajenos a el— sin
     * ser parte de ninguna de esas operaciones. Administrar un grupo es poder
     * editarlo y avisar a los morosos, no mirar los recibos de los demas; y en
     * un estudio de campo esa diferencia es justo la que sostiene lo que la
     * memoria promete sobre el tratamiento de los datos.</p>
     */
    private boolean canViewEvidence(Payment payment, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return false;
        return userRepository.findByUsername(authentication.getName())
                .map(user -> payment.getUser().getId().equals(user.getId())
                        || payment.getExpense().getUser().getId().equals(user.getId()))
                .orElse(false);
    }

    private PaymentResource toPaymentResource(Payment payment) {
        // El eslabon se busca una sola vez y se sacan de el las dos cosas. Antes
        // solo se leia el hash; pedir ahora la marca de tiempo con una segunda
        // consulta duplicaria el viaje a la base para leer la misma fila.
        var record = expenseChainRecordRepository
                .findFirstByPayment_IdOrderByRecordIndexDesc(payment.getId());

        return PaymentResourceFromEntityAssembler.toResourceFromEntity(
                payment,
                false,
                record.map(chainRecord -> chainRecord.getTransactionHash().hash()).orElse(""),
                record.map(chainRecord -> chainRecord.getCreatedAt()).orElse(null)
        );
    }
}

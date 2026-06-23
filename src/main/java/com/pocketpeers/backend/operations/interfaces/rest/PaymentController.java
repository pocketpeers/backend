package com.pocketpeers.backend.operations.interfaces.rest;

import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.commands.ConfirmPaymentCommand;
import com.pocketpeers.backend.operations.domain.model.queries.*;
import com.pocketpeers.backend.operations.domain.model.valueobjects.PaymentStatus;
import com.pocketpeers.backend.operations.domain.services.PaymentCommandService;
import com.pocketpeers.backend.operations.domain.services.PaymentQueryService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ContractTransactionRepository;
import com.pocketpeers.backend.operations.interfaces.rest.resources.MakePaymentResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreatePaymentResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.PaymentResource;
import com.pocketpeers.backend.operations.interfaces.rest.transform.MakePaymentCommandFromResourceAssembler;
import com.pocketpeers.backend.operations.interfaces.rest.transform.CreatePaymentCommandFromResourceAssembler;
import com.pocketpeers.backend.operations.interfaces.rest.transform.PaymentResourceFromEntityAssembler;
import com.pocketpeers.backend.shared.interfaces.rest.resources.MessageResource;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
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
    private final GroupMemberRepository groupMemberRepository;
    private final ContractTransactionRepository contractTransactionRepository;

    public PaymentController(PaymentQueryService paymentQueryService, PaymentCommandService paymentCommandService,
                             UserRepository userRepository, GroupMemberRepository groupMemberRepository,
                             ContractTransactionRepository contractTransactionRepository) {
        this.paymentQueryService = paymentQueryService;
        this.paymentCommandService = paymentCommandService;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.contractTransactionRepository = contractTransactionRepository;
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

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResource> getPaymentById(@PathVariable Long paymentId, Authentication authentication) {
        var getPaymentByIdQuery = new GetPaymentByIdQuery(paymentId);
        var payment = paymentQueryService.handle(getPaymentByIdQuery);
        var paymentResource = PaymentResourceFromEntityAssembler.toResourceFromEntity(
                payment.get(),
                canViewEvidence(payment.get(), authentication),
                paymentBlockchainHash(payment.get().getId())
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

    private boolean canViewEvidence(Payment payment, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return false;
        return userRepository.findByUsername(authentication.getName())
                .map(user -> payment.getExpense().getUser().getId().equals(user.getId())
                        || groupMemberRepository.findByGroupIdAndUser_Id(
                                payment.getExpense().getGroup().getId(),
                                user.getId()
                        )
                        .map(member -> member.getRole() == GroupRole.ADMIN)
                        .orElse(false))
                .orElse(false);
    }

    private PaymentResource toPaymentResource(Payment payment) {
        return PaymentResourceFromEntityAssembler.toResourceFromEntity(
                payment,
                false,
                paymentBlockchainHash(payment.getId())
        );
    }

    private String paymentBlockchainHash(Long paymentId) {
        return contractTransactionRepository
                .findFirstByPayment_IdOrderByCreatedAtDesc(paymentId)
                .map(transaction -> transaction.getTransactionHash().hash())
                .orElse("");
    }
}

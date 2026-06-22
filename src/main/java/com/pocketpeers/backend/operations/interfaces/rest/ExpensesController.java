package com.pocketpeers.backend.operations.interfaces.rest;

import com.pocketpeers.backend.operations.domain.model.commands.CreateExpenseCommand;
import com.pocketpeers.backend.operations.domain.model.commands.DeleteExpenseCommand;
import com.pocketpeers.backend.operations.domain.model.commands.CreatePaymentCommand;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllExpensesByGroupIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllExpensesByUserIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllExpensesQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetExpenseByIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetPaymentByIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.SearchExpensesByNameQuery;
import com.pocketpeers.backend.operations.domain.services.ExpenseCommandService;
import com.pocketpeers.backend.operations.domain.services.ExpenseQueryService;
import com.pocketpeers.backend.operations.domain.services.PaymentCommandService;
import com.pocketpeers.backend.operations.domain.services.PaymentQueryService;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreateExpenseResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.CreateExpenseWithPaymentsResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ExpenseResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.ExpenseWithPaymentsResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.PaymentResource;
import com.pocketpeers.backend.operations.interfaces.rest.resources.UpdateExpenseResource;
import com.pocketpeers.backend.operations.interfaces.rest.transform.ExpenseResourceFromEntityAssembler;
import com.pocketpeers.backend.operations.interfaces.rest.transform.PaymentResourceFromEntityAssembler;
import com.pocketpeers.backend.operations.interfaces.rest.transform.UpdateExpenseCommandFromResourceAssembler;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "/api/v1/expenses", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name="Expenses", description = "Expenses Management Endpoints")
public class ExpensesController {

    private final ExpenseQueryService expenseQueryService;
    private final ExpenseCommandService expenseCommandService;
    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;
    private final UserRepository userRepository;

    public ExpensesController(ExpenseQueryService expenseQueryService, ExpenseCommandService expenseCommandService,
                              PaymentCommandService paymentCommandService, PaymentQueryService paymentQueryService,
                              UserRepository userRepository) {
        this.expenseQueryService = expenseQueryService;
        this.expenseCommandService = expenseCommandService;
        this.paymentCommandService = paymentCommandService;
        this.paymentQueryService = paymentQueryService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ExpenseResource> createExpense(@RequestBody CreateExpenseResource resource, Authentication authentication) {
        var user = authenticatedUser(authentication);
        var createExpenseCommand = new CreateExpenseCommand(
                resource.name(),
                resource.amount(),
                user.getId(),
                resource.groupId(),
                resource.dueDate()
        );
        var expenseId = expenseCommandService.handle(createExpenseCommand);
        //var getExpenseByNameAndUserId = new GetExpenseByNameAndUserInformationIdQuery(new ExpenseName(resource.name()), resource.requesterId());
        //var expense = expenseQueryService.handle(getExpenseByNameAndUserId);
        //if (expense.isEmpty()) return ResponseEntity.badRequest().build();
        if (expenseId.isEmpty()) return ResponseEntity.badRequest().build();
        var expenseResource = ExpenseResourceFromEntityAssembler.toResourceFromEntity(expenseId.get());
        return new ResponseEntity<>(expenseResource, HttpStatus.CREATED);
    }

    @PostMapping("/with-payments")
    @Transactional
    public ResponseEntity<ExpenseWithPaymentsResource> createExpenseWithPayments(@RequestBody CreateExpenseWithPaymentsResource resource, Authentication authentication) {
        var user = authenticatedUser(authentication);
        if (resource.payments() == null || resource.payments().isEmpty()) {
            throw new IllegalArgumentException("At least one payment is required");
        }
        var paymentsTotal = resource.payments().stream()
                .map(payment -> payment.amount() == null ? BigDecimal.ZERO : payment.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paymentsTotal.compareTo(resource.amount()) != 0) {
            throw new IllegalArgumentException("Payment amounts must match the expense amount");
        }

        var createExpenseCommand = new CreateExpenseCommand(
                resource.name(),
                resource.amount(),
                user.getId(),
                resource.groupId(),
                resource.dueDate()
        );
        var expense = expenseCommandService.handle(createExpenseCommand)
                .orElseThrow(() -> new IllegalArgumentException("Expense could not be created"));

        var paymentResources = new ArrayList<PaymentResource>();
        for (var paymentResource : resource.payments()) {
            var command = new CreatePaymentCommand(
                    paymentResource.description(),
                    paymentResource.amount(),
                    paymentResource.userId(),
                    expense.getId()
            );
            var paymentId = paymentCommandService.handle(command);
            var payment = paymentQueryService.handle(new GetPaymentByIdQuery(paymentId))
                    .orElseThrow(() -> new IllegalArgumentException("Payment could not be created"));
            paymentResources.add(PaymentResourceFromEntityAssembler.toResourceFromEntity(payment));
        }

        var response = new ExpenseWithPaymentsResource(
                ExpenseResourceFromEntityAssembler.toResourceFromEntity(expense),
                paymentResources
        );
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResource> getExpenseById(@PathVariable Long expenseId) {
        var getExpenseByIdQuery = new GetExpenseByIdQuery(expenseId);
        var expense = expenseQueryService.handle(getExpenseByIdQuery);
        if (expense.isEmpty()) return ResponseEntity.badRequest().build();
        var expenseResource = ExpenseResourceFromEntityAssembler.toResourceFromEntity(expense.get());
        return ResponseEntity.ok(expenseResource);
    }

    @GetMapping("/userId/{userId}")
    public ResponseEntity<List<ExpenseResource>> getExpensesByUserId(@PathVariable Long userId) {
        var getAllExpensesByUserIdQuery = new GetAllExpensesByUserIdQuery(userId);
        var expenses = expenseQueryService.handle(getAllExpensesByUserIdQuery);
        var expenseResources = expenses.stream().map(ExpenseResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(expenseResources);
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResource>> getAllExpenses() {
        var getAllExpensesQuery = new GetAllExpensesQuery();
        var expenses = expenseQueryService.handle(getAllExpensesQuery);
        var expensesResources = expenses.stream().map(ExpenseResourceFromEntityAssembler::toResourceFromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(expensesResources);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ExpenseResource>> searchExpensesByName(@RequestParam String name) {
        var expenses = expenseQueryService.handle(new SearchExpensesByNameQuery(name));
        var expenseResources = expenses.stream().map(ExpenseResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(expenseResources);
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<ExpenseResource> updateExpense(@PathVariable Long expenseId, @RequestBody UpdateExpenseResource updateExpenseResource) {
        var updateExpenseCommand = UpdateExpenseCommandFromResourceAssembler.toCommandFromResource(expenseId, updateExpenseResource);
        var updatedExpense = expenseCommandService.handle(updateExpenseCommand);
        if(updatedExpense.isEmpty()) return ResponseEntity.badRequest().build();
        var expenseResource = ExpenseResourceFromEntityAssembler.toResourceFromEntity(updatedExpense.get());
        return ResponseEntity.ok(expenseResource);
    }

    @GetMapping("/groupId/{groupId}")
    public ResponseEntity<List<ExpenseResource>> getExpensesByGroupId(@PathVariable Long groupId) {
        var getAllExpensesByGroupIdQuery = new GetAllExpensesByGroupIdQuery(groupId);
        var expenses = expenseQueryService.handle(getAllExpensesByGroupIdQuery);
        var expenseResources = expenses.stream().map(ExpenseResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(expenseResources);
    }

    @DeleteMapping("expenseId/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long expenseId) {
        var deleteExpenseCommand = new DeleteExpenseCommand(expenseId);
        expenseCommandService.handle(deleteExpenseCommand);
        return ResponseEntity.noContent().build();
    }

}

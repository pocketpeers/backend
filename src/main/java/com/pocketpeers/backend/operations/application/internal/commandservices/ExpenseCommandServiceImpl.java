package com.pocketpeers.backend.operations.application.internal.commandservices;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.aggregates.GroupOperation;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupOperationRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupRepository;
import com.pocketpeers.backend.operations.application.internal.queryservices.ExpenseQueryServiceImpl;
import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.domain.model.commands.CreateExpenseCommand;
import com.pocketpeers.backend.operations.domain.model.commands.DeleteExpenseCommand;
import com.pocketpeers.backend.operations.domain.model.commands.UpdateExpenseCommand;
import com.pocketpeers.backend.operations.domain.model.events.ExpenseCreatedEvent;
import com.pocketpeers.backend.operations.domain.services.ExpenseCommandService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ExpenseCommandServiceImpl implements ExpenseCommandService {
    private final PaymentRepository paymentRepository;
    private final GroupOperationRepository groupOperationRepository;
    private final ExpenseRepository expenseRepository;
    private final UserInformationRepository userInformationRepository;
    private final GroupRepository groupRepository;
    private final ExpenseQueryServiceImpl expenseQueryServiceImpl;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ExpenseCommandServiceImpl(PaymentRepository paymentRepository, GroupOperationRepository groupOperationRepository, ExpenseRepository expenseRepository, UserInformationRepository userInformationRepository, GroupRepository groupRepository, ExpenseQueryServiceImpl expenseQueryServiceImpl, ApplicationEventPublisher applicationEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.groupOperationRepository = groupOperationRepository;
        this.expenseRepository = expenseRepository;
        this.userInformationRepository = userInformationRepository;
        this.groupRepository = groupRepository;
        this.expenseQueryServiceImpl = expenseQueryServiceImpl;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public Optional<Expense> handle(CreateExpenseCommand command) {
        Optional<UserInformation> user = userInformationRepository.findById(command.userId());
        Optional<Group> group = groupRepository.findById(command.groupId());

        if (user.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        Expense expense = new Expense(command.name(), command.amount(), user.get(), group.get(), command.dueDate());
        expenseRepository.save(expense);

        applicationEventPublisher.publishEvent(new ExpenseCreatedEvent(expense));

        return expenseRepository.findById(expense.getId());
    }

    @Override
    public Optional<Expense> handle(UpdateExpenseCommand command) {
        var result = expenseRepository.findById(command.id());
        if (result.isEmpty()) {throw new IllegalArgumentException("Expense not found");}
        var expenseToUpdate = result.get();
        try {
            var updateExpense = expenseRepository.save(expenseToUpdate.UpdateInformation(command.name(), command.amount(), command.dueDate()));
            return Optional.of(updateExpense);
        }catch (Exception e) {
            throw new IllegalArgumentException("Error while updating expense: " + e.getMessage());
        }
    }

    @Transactional
    @Override
    public void handle(DeleteExpenseCommand command) {
        if (!expenseRepository.existsById(command.expenseId()))
            throw new IllegalArgumentException("Expense does not exists");

        List<GroupOperation> groupOperations = groupOperationRepository.findByExpenseId(command.expenseId());
        groupOperationRepository.deleteAll(groupOperations);
        List<Payment> payments = paymentRepository.findAllByExpenseId(command.expenseId());
        paymentRepository.deleteAll(payments);

        expenseRepository.deleteById(command.expenseId());
    }
}

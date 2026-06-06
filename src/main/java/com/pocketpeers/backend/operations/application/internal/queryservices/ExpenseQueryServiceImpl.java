package com.pocketpeers.backend.operations.application.internal.queryservices;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.queries.*;
import com.pocketpeers.backend.operations.domain.model.valueobjects.DueDate;
import com.pocketpeers.backend.operations.domain.services.ExpenseQueryService;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ExpenseQueryServiceImpl implements ExpenseQueryService {

    private final ExpenseRepository expenseRepository;

    public ExpenseQueryServiceImpl(ExpenseRepository expenseRepository) {this.expenseRepository = expenseRepository;}

    @Override
    public List<Expense> handle(GetAllExpensesQuery query) {
        return expenseRepository.findAll();
    }

    @Override
    public Optional<Expense> handle(GetExpenseByIdQuery query){
        return expenseRepository.findById(query.expenseId());
    }

    @Override
    public List<Expense> handle(GetAllExpensesByUserInformationIdQuery query) {
        return expenseRepository.findByUserInformationId(query.userInformationId());
    }

    @Override
    public Optional<Expense> handle(GetExpenseByNameAndUserInformationIdQuery query){
        return expenseRepository.findByNameAndId(query.expenseName(), query.userInformationId());
    }

    @Override
    public List<Expense> handle(GetAllExpensesByGroupIdQuery query){
        return expenseRepository.findByGroupId(query.groupId());
    }

    @Override
    public List<Expense> handle(GetAllExpensesByDueDate query) {
        LocalDate localDate = query.dueDate();
        DueDate dueDate = new DueDate(localDate);
        return expenseRepository.findAllByDueDate(dueDate);
    }

}

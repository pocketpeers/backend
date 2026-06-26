package com.pocketpeers.backend.operations.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.pocketpeers.backend.operations.domain.model.aggregates.Expense;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllExpensesByDueDate;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllExpensesByGroupIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllExpensesByUserIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetAllExpensesQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetExpenseByIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.GetExpenseByNameAndUserIdQuery;
import com.pocketpeers.backend.operations.domain.model.queries.SearchExpensesByNameQuery;
import com.pocketpeers.backend.operations.domain.model.valueobjects.DueDate;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseQueryServiceImplTests {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private ExpenseQueryServiceImpl service;

    @Test
    void getAllExpensesReturnsOnlyActiveExpenses() {
        Expense active = expense();
        Expense cancelled = expense().cancel();
        when(expenseRepository.findAll()).thenReturn(List.of(active, cancelled));

        assertThat(service.handle(new GetAllExpensesQuery())).containsExactly(active);
    }

    @Test
    void getByIdAndByNameDelegateToRepository() {
        Expense expense = expense();
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
        when(expenseRepository.findByNameAndUser_Id("Cena", 2L)).thenReturn(Optional.of(expense));

        assertThat(service.handle(new GetExpenseByIdQuery(1L))).contains(expense);
        assertThat(service.handle(new GetExpenseByNameAndUserIdQuery("Cena", 2L))).contains(expense);
    }

    @Test
    void filtersUserGroupAndDueDateQueriesByActiveExpenses() {
        LocalDate dueDateValue = LocalDate.now().plusDays(2);
        Expense active = expense();
        Expense cancelled = expense().cancel();
        when(expenseRepository.findByUser_Id(2L)).thenReturn(List.of(active, cancelled));
        when(expenseRepository.findByGroupId(3L)).thenReturn(List.of(active, cancelled));
        when(expenseRepository.findAllByDueDate(new DueDate(dueDateValue))).thenReturn(List.of(active, cancelled));

        assertThat(service.handle(new GetAllExpensesByUserIdQuery(2L))).containsExactly(active);
        assertThat(service.handle(new GetAllExpensesByGroupIdQuery(3L))).containsExactly(active);
        assertThat(service.handle(new GetAllExpensesByDueDate(dueDateValue))).containsExactly(active);
    }

    @Test
    void searchReturnsEmptyForBlankAndTrimsName() {
        Expense active = expense();
        Expense cancelled = expense().cancel();
        when(expenseRepository.findAllByNameIgnoreCase("Cena")).thenReturn(List.of(active, cancelled));

        assertThat(service.handle(new SearchExpensesByNameQuery(" "))).isEmpty();
        assertThat(service.handle(new SearchExpensesByNameQuery(" Cena "))).containsExactly(active);
        verify(expenseRepository).findAllByNameIgnoreCase("Cena");
    }

    private Expense expense() {
        return new Expense("Cena", new BigDecimal("50.00"), null, null, LocalDate.now().plusDays(3));
    }
}

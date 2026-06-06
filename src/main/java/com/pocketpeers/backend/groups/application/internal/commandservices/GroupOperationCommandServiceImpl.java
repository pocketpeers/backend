package com.pocketpeers.backend.groups.application.internal.commandservices;

import com.pocketpeers.backend.groups.domain.exceptions.GroupNotFoundException;
import com.pocketpeers.backend.groups.domain.exceptions.PaymentNotFoundException;
import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.aggregates.GroupOperation;
import com.pocketpeers.backend.groups.domain.model.commands.AddGroupOperationCommand;
import com.pocketpeers.backend.groups.domain.model.commands.DeleteGroupOperationCommand;
import com.pocketpeers.backend.groups.domain.services.GroupOperationCommandService;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupOperationRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupRepository;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.ExpenseRepository;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import org.springframework.stereotype.Service;

@Service
public class GroupOperationCommandServiceImpl implements GroupOperationCommandService {
    private final GroupOperationRepository groupOperationRepository;
    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository paymentRepository;
    private final GroupMemberRepository groupMemberRepository;

    public GroupOperationCommandServiceImpl(GroupOperationRepository groupOperationRepository, GroupRepository groupRepository, ExpenseRepository expenseRepository, PaymentRepository paymentRepository, GroupMemberRepository groupMemberRepository) {
        this.groupOperationRepository = groupOperationRepository;
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.paymentRepository = paymentRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    public Long handle(AddGroupOperationCommand command) {
        expenseRepository.findById(command.expenseId()).map(expense -> {
            Group group = groupRepository.findById(command.groupId()).orElseThrow(() -> new GroupNotFoundException(command.groupId()));
            Payment payment = paymentRepository.findById(command.paymentId()).orElseThrow(() -> new PaymentNotFoundException(command.paymentId()));
            GroupOperation groupOperation = new GroupOperation(group, expense, payment);
            groupOperation = groupOperationRepository.save(groupOperation);
            return groupOperation.getId();
        }).orElseThrow(() -> new RuntimeException("Group not found"));
        return 0L;
    }


    public void handle(DeleteGroupOperationCommand command) {
        if(!groupOperationRepository.existsById(command.groupOperationId())){
            throw new IllegalArgumentException("Group does not exist");
        }
        try{
            groupOperationRepository.deleteById(command.groupOperationId());
        } catch(Exception e){
            throw new IllegalArgumentException("Error while deleting group operation: " + e.getMessage());
        }
    }
}

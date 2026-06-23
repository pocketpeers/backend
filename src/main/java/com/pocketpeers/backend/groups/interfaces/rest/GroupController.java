package com.pocketpeers.backend.groups.interfaces.rest;

import com.pocketpeers.backend.groups.domain.model.commands.*;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsByUserIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetGroupByIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.SearchGroupsByNameQuery;
import com.pocketpeers.backend.groups.domain.services.GroupCommandService;
import com.pocketpeers.backend.groups.domain.services.GroupQueryService;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.interfaces.rest.resources.CreateGroupResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.GroupResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.OverdueMemberResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.OverduePaymentDebtResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.UpdateGroupImageResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.UpdateGroupResource;
import com.pocketpeers.backend.groups.interfaces.rest.transform.CreateGroupCommandFromResourceAssembler;
import com.pocketpeers.backend.groups.interfaces.rest.transform.GroupResourceFromEntityAssembler;
import com.pocketpeers.backend.operations.domain.model.aggregates.Payment;
import com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories.PaymentRepository;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})

@RequestMapping(value = "api/v1/groups", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Group", description = "Group Management Endpoints")
public class GroupController {

    private final GroupCommandService groupCommandService;
    private final GroupQueryService groupQueryService;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    public GroupController(GroupCommandService groupCommandService, GroupQueryService groupQueryService,
                           GroupMemberRepository groupMemberRepository, UserRepository userRepository,
                           PaymentRepository paymentRepository) {
        this.groupCommandService = groupCommandService;
        this.groupQueryService = groupQueryService;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    @Operation(summary = "Create a new group")
    @PostMapping
    public ResponseEntity<GroupResource> createGroup(@RequestBody CreateGroupResource createGroupResource) {
        var createGroupCommand = CreateGroupCommandFromResourceAssembler.toCommandFromResource(createGroupResource);
        var groupId = groupCommandService.handle(createGroupCommand);
        if (groupId == 0L)  return ResponseEntity.badRequest().build();
        var getGroupByIdQuery = new GetGroupByIdQuery(groupId);
        var group = groupQueryService.handle(getGroupByIdQuery);
        if(group.isEmpty()) return ResponseEntity.badRequest().build();
        var groupResource = GroupResourceFromEntityAssembler.toResourceFromEntity(group.get());
        return new ResponseEntity<>(groupResource, HttpStatus.CREATED);
    }

    @Operation(summary = "Get by group Id")
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResource> getGroupById(@PathVariable Long groupId) {
        var getGroupByIdQuery = new GetGroupByIdQuery(groupId);
        var group = groupQueryService.handle(getGroupByIdQuery);
        if (group.isEmpty()) return ResponseEntity.badRequest().build();
        var groupResource = GroupResourceFromEntityAssembler.toResourceFromEntity(group.get());
        return ResponseEntity.ok(groupResource);
    }

    @Operation(summary = "Get all groups")
    @GetMapping
    public ResponseEntity<List<GroupResource>> getAllGroups() {
        var getAllGroupsQuery = new GetAllGroupsQuery();
        var groups = groupQueryService.handle(getAllGroupsQuery);
        var groupResources = groups.stream().map(GroupResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(groupResources);
    }

    @Operation(summary = "Search groups by name")
    @GetMapping("/search")
    public ResponseEntity<List<GroupResource>> searchGroupsByName(@RequestParam String name) {
        var groups = groupQueryService.handle(new SearchGroupsByNameQuery(name));
        var groupResources = groups.stream().map(GroupResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(groupResources);
    }

    @Operation(summary = "Get all groups by user ID")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<GroupResource>> getAllGroupsByUserId(@PathVariable Long userId) {
        var query = new GetAllGroupsByUserIdQuery(userId);
        var groups = groupQueryService.handle(query);
        //if (groups.isEmpty()) return ResponseEntity.notFound().build();
        var groupResources = groups.stream().map(GroupResourceFromEntityAssembler::toResourceFromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(groupResources);
    }

    @Operation(summary = "Update group image by group ID")
    @PutMapping("/{groupId}/image")
    public ResponseEntity<GroupResource> updateGroupImage(@PathVariable Long groupId,
                                                          @RequestBody UpdateGroupImageResource updateGroupImageResource,
                                                          Authentication authentication) {
        if (!isAuthenticatedGroupAdmin(groupId, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var updateGroupImageCommand = new UpdateGroupImageCommand(groupId, updateGroupImageResource.image());
        var updatedGroup = groupCommandService.handle(updateGroupImageCommand);
        if (updatedGroup.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var groupResource = GroupResourceFromEntityAssembler.toResourceFromEntity(updatedGroup.get());
        return ResponseEntity.ok(groupResource);
    }


    @Operation(summary = "Update group description and name by group ID")
    @PutMapping("/{groupId}")
    public ResponseEntity<GroupResource> updateGroupDescriptionAndName(@PathVariable Long groupId,
                                                                       @RequestBody UpdateGroupResource updateGroupResource,
                                                                       Authentication authentication) {
        if (!isAuthenticatedGroupAdmin(groupId, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var updateGroupDescriptionAndNameCommand = new UpdateGroupCommand(groupId, updateGroupResource.name(), updateGroupResource.description());
        var updatedGroup = groupCommandService.handle(updateGroupDescriptionAndNameCommand);
        if (updatedGroup.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var groupResource = GroupResourceFromEntityAssembler.toResourceFromEntity(updatedGroup.get());
        return ResponseEntity.ok(groupResource);
    }

    @Operation(summary = "Delete by group ID")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> deleteGroup(@PathVariable Long groupId) {
        var deleteGroupCommand = new DeleteGroupCommand(groupId);
        groupCommandService.handle(deleteGroupCommand);
        return ResponseEntity.ok("Group with given id successfully deleted");
    }

    @PostMapping("/{groupId}/generate-invitation")
    public ResponseEntity<String> generateInvitation(@PathVariable Long groupId) {
        var command = new GenerateInvitationCommand(groupId);
        var token = groupCommandService.handle(command);
        return ResponseEntity.ok(token);
    }

    @Operation(summary = "Get overdue members in a group")
    @GetMapping("/{groupId}/overdue-members")
    public ResponseEntity<List<OverdueMemberResource>> getOverdueMembers(@PathVariable Long groupId) {
        var today = LocalDate.now();
        var payments = paymentRepository.findOverduePaymentsByGroupId(groupId, today);
        Map<Long, OverdueMemberAccumulator> members = new LinkedHashMap<>();

        for (var payment : payments) {
            var user = payment.getUser();
            var accumulator = members.computeIfAbsent(user.getId(), userId ->
                    new OverdueMemberAccumulator(userId, fullName(payment), photo(payment)));
            accumulator.add(payment, today);
        }

        var resources = members.values().stream()
                .map(OverdueMemberAccumulator::toResource)
                .sorted(Comparator
                        .comparing(OverdueMemberResource::maxDaysOverdue, Comparator.reverseOrder())
                        .thenComparing(OverdueMemberResource::overdueAmount, Comparator.reverseOrder()))
                .toList();

        return ResponseEntity.ok(resources);
    }

    @Operation(summary = "Get overdue debts for a group member")
    @GetMapping("/{groupId}/overdue-members/{memberId}")
    public ResponseEntity<List<OverduePaymentDebtResource>> getOverdueMemberDebts(
            @PathVariable Long groupId,
            @PathVariable Long memberId
    ) {
        var today = LocalDate.now();
        var resources = paymentRepository.findOverduePaymentsByGroupIdAndUserId(groupId, memberId, today)
                .stream()
                .map(payment -> toDebtResource(payment, today))
                .toList();

        return ResponseEntity.ok(resources);
    }

    private boolean isAuthenticatedGroupAdmin(Long groupId, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(user -> groupMemberRepository.existsByGroupIdAndUser_IdAndRole(groupId, user.getId(), GroupRole.ADMIN))
                .orElse(false);
    }

    private OverduePaymentDebtResource toDebtResource(Payment payment, LocalDate today) {
        var expense = payment.getExpense();
        var dueDate = expense.getDueDate();
        return new OverduePaymentDebtResource(
                payment.getId(),
                expense.getId(),
                expense.getName(),
                expense.getGroup().getName(),
                payment.getAmount(),
                amountPaid(payment),
                overdueAmount(payment),
                dueDate,
                daysOverdue(dueDate, today),
                payment.getStatus(),
                payment.getConfirmed()
        );
    }

    private static BigDecimal overdueAmount(Payment payment) {
        return payment.getAmount().subtract(amountPaid(payment));
    }

    private static BigDecimal amountPaid(Payment payment) {
        return payment.getAmountPaid() == null ? BigDecimal.ZERO : payment.getAmountPaid();
    }

    private static long daysOverdue(LocalDate dueDate, LocalDate today) {
        return ChronoUnit.DAYS.between(dueDate, today);
    }

    private static String fullName(Payment payment) {
        UserInformation userInformation = payment.getUser().getUserInformation();
        if (userInformation == null) {
            return payment.getUser().getUsername();
        }
        return userInformation.getFullName();
    }

    private static String photo(Payment payment) {
        UserInformation userInformation = payment.getUser().getUserInformation();
        if (userInformation == null) {
            return "";
        }
        return userInformation.getPhoto();
    }

    private static class OverdueMemberAccumulator {
        private final Long userId;
        private final String fullName;
        private final String photo;
        private BigDecimal overdueAmount = BigDecimal.ZERO;
        private LocalDate oldestDueDate;
        private long maxDaysOverdue = 0;
        private int overduePaymentsCount = 0;

        private OverdueMemberAccumulator(Long userId, String fullName, String photo) {
            this.userId = userId;
            this.fullName = fullName;
            this.photo = photo;
        }

        private void add(Payment payment, LocalDate today) {
            var dueDate = payment.getExpense().getDueDate();
            overdueAmount = overdueAmount.add(GroupController.overdueAmount(payment));
            overduePaymentsCount++;
            if (oldestDueDate == null || dueDate.isBefore(oldestDueDate)) {
                oldestDueDate = dueDate;
            }
            maxDaysOverdue = Math.max(maxDaysOverdue, daysOverdue(dueDate, today));
        }

        private OverdueMemberResource toResource() {
            return new OverdueMemberResource(
                    userId,
                    fullName,
                    photo,
                    overdueAmount,
                    oldestDueDate,
                    maxDaysOverdue,
                    overduePaymentsCount
            );
        }
    }

}

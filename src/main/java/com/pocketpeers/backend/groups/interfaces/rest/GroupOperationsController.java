package com.pocketpeers.backend.groups.interfaces.rest;

import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupOperationsByGroupIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupOperationsQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetGroupOperationByGroupIdAndExpenseIdAndPaymentId;
import com.pocketpeers.backend.groups.domain.model.queries.GetGroupOperationByIdQuery;
import com.pocketpeers.backend.groups.domain.services.GroupOperationCommandService;
import com.pocketpeers.backend.groups.domain.services.GroupOperationQueryService;
import com.pocketpeers.backend.groups.interfaces.rest.resources.AddGroupOperationResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.GroupOperationResource;
import com.pocketpeers.backend.groups.interfaces.rest.transform.AddGroupOperationCommandFromResourceAssembler;
import com.pocketpeers.backend.groups.interfaces.rest.transform.GroupOperationResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "api/v1/groupOperations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "GroupOperations",description = "Group Operations Management Endpoint")
public class GroupOperationsController {
    private final GroupOperationQueryService groupOperationQueryService;
    private final GroupOperationCommandService groupOperationCommandService;

    public GroupOperationsController(GroupOperationQueryService groupOperationQueryService, GroupOperationCommandService groupOperationCommandService) {
        this.groupOperationQueryService = groupOperationQueryService;
        this.groupOperationCommandService = groupOperationCommandService;
    }

    @PostMapping
    public ResponseEntity<GroupOperationResource> addGroupOperation(@RequestBody AddGroupOperationResource resource) {
        var command = AddGroupOperationCommandFromResourceAssembler.toCommandFromResource(resource);
        var groupOperationId = groupOperationCommandService.handle(command);
        System.out.println("Payment ID: " + groupOperationId);
        var getGroupOperationByGroupIdAndExpenseIdAndPaymentId = new GetGroupOperationByGroupIdAndExpenseIdAndPaymentId(resource.groupId(), resource.expenseId(), resource.paymentId());
        var groupOperation = groupOperationQueryService.handle(getGroupOperationByGroupIdAndExpenseIdAndPaymentId);
        if (groupOperation.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var groupOperationResource = GroupOperationResourceFromEntityAssembler.toResourceFromEntity(groupOperation.get());
        return new ResponseEntity<>(groupOperationResource, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<GroupOperationResource>> getAllGroupOperations(){
        var getAllGroupOperationsQuery = new GetAllGroupOperationsQuery();
        var groupOperations = groupOperationQueryService.handle(getAllGroupOperationsQuery);
        var groupOperationResources = groupOperations.stream().map(GroupOperationResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(groupOperationResources);
    }

    @GetMapping("/{groupOperationId}")
    public ResponseEntity<GroupOperationResource> getGroupOperationById(@PathVariable Long groupOperationId){
        var getGroupOperationByIdQuery = new GetGroupOperationByIdQuery(groupOperationId);
        var groupOperation = groupOperationQueryService.handle(getGroupOperationByIdQuery);
        if (groupOperation.isEmpty()) {return ResponseEntity.badRequest().build();}
        var groupOperationResource = GroupOperationResourceFromEntityAssembler.toResourceFromEntity(groupOperation.get());
        return ResponseEntity.ok(groupOperationResource);
    }

    @GetMapping("/groupId/{groupId}")
    public ResponseEntity<List<GroupOperationResource>> getGroupOperationByGroupId(@PathVariable Long groupId){
        var getAllGroupOperationsByGroupIdQuery = new GetAllGroupOperationsByGroupIdQuery(groupId);
        var groupOperations = groupOperationQueryService.handle(getAllGroupOperationsByGroupIdQuery);
        if (groupOperations.isEmpty()) {return ResponseEntity.badRequest().build();}
        var groupOperationResources = groupOperations.stream().map(GroupOperationResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(groupOperationResources);
    }
}

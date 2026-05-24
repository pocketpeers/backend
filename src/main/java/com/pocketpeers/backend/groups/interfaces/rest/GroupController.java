package com.pocketpeers.backend.groups.interfaces.rest;

import com.pocketpeers.backend.groups.domain.model.commands.*;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsByUserIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetGroupByIdQuery;
import com.pocketpeers.backend.groups.domain.services.GroupCommandService;
import com.pocketpeers.backend.groups.domain.services.GroupQueryService;
import com.pocketpeers.backend.groups.interfaces.rest.resources.CreateGroupResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.GroupResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.UpdateGroupImageResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.UpdateGroupResource;
import com.pocketpeers.backend.groups.interfaces.rest.transform.CreateGroupCommandFromResourceAssembler;
import com.pocketpeers.backend.groups.interfaces.rest.transform.GroupResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})

@RequestMapping(value = "api/v1/groups", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Group", description = "Group Management Endpoints")
public class GroupController {

    private final GroupCommandService groupCommandService;
    private final GroupQueryService groupQueryService;

    public GroupController(GroupCommandService groupCommandService, GroupQueryService groupQueryService) {
        this.groupCommandService = groupCommandService;
        this.groupQueryService = groupQueryService;
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

    @Operation(summary = "Get all groups by user ID")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<GroupResource>> getAllGroupsByUserId(@PathVariable Long userId) {
        var query = new GetAllGroupsByUserIdQuery(userId);
        var groups = groupQueryService.handle(query);
        if (groups.isEmpty()) return ResponseEntity.notFound().build();
        var groupResources = groups.stream().map(GroupResourceFromEntityAssembler::toResourceFromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(groupResources);
    }

    @Operation(summary = "Update group image by group ID")
    @PutMapping("/{groupId}/image")
    public ResponseEntity<GroupResource> updateGroupImage(@PathVariable Long groupId, @RequestBody UpdateGroupImageResource updateGroupImageResource) {
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
    public ResponseEntity<GroupResource> updateGroupDescriptionAndName(@PathVariable Long groupId, @RequestBody UpdateGroupResource updateGroupResource) {
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




}

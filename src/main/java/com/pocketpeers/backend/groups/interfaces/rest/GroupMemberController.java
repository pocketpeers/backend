package com.pocketpeers.backend.groups.interfaces.rest;

import com.pocketpeers.backend.groups.domain.model.commands.AddMemberCommand;
import com.pocketpeers.backend.groups.domain.model.commands.JoinGroupWithTokenCommand;
import com.pocketpeers.backend.groups.domain.model.commands.RemoveMemberCommand;
import com.pocketpeers.backend.groups.domain.model.queries.GetALLGroupByUserIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllMembersInGroupQuery;
import com.pocketpeers.backend.groups.domain.services.GroupMemberCommandService;
import com.pocketpeers.backend.groups.domain.services.GroupMemberQueryService;
import com.pocketpeers.backend.groups.interfaces.rest.resources.GroupMemberResource;
import com.pocketpeers.backend.groups.interfaces.rest.resources.JoinGroupWithTokenResource;
import com.pocketpeers.backend.groups.interfaces.rest.transform.GroupMemberResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(value = "api/v1/groups",  produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Member Group", description = "Member Group Management Endpoints")
public class GroupMemberController {

    private final GroupMemberCommandService groupMemberCommandService;
    private final GroupMemberQueryService groupMemberQueryService;

    public GroupMemberController(GroupMemberCommandService groupMemberCommandService, GroupMemberQueryService groupMemberQueryService) {
        this.groupMemberCommandService = groupMemberCommandService;
        this.groupMemberQueryService = groupMemberQueryService;
    }

    @Operation(summary = "Get all groups by user ID", description = "Retrieves all groups associated with the specified user ID.")
    @GetMapping("/members/{userId}")
    public ResponseEntity<List<GroupMemberResource>> getGroupsByUserId(@PathVariable Long userId) {
        var groups = groupMemberQueryService.handle(new GetALLGroupByUserIdQuery(userId));
        if (groups.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        var groupMemberResources = groups.stream()
                .map(GroupMemberResourceFromEntityAssembler::fromEntityToResource)
                .collect(Collectors.toList());
        return ResponseEntity.ok(groupMemberResources);
    }

    @Operation(summary = "Add member to group")
    @PostMapping("/{groupId}/members/{userId}")
    public ResponseEntity<GroupMemberResource> addMember( @PathVariable Long groupId, @PathVariable Long userId) {
        var addMemberCommand = new AddMemberCommand(groupId, userId);
        var addedMember = groupMemberCommandService.handle(addMemberCommand);

        if (addedMember.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        var groupMemberResource = GroupMemberResourceFromEntityAssembler.fromEntityToResource(addedMember.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(groupMemberResource);
    }

    @Operation(summary = "Remove member from group")
    @DeleteMapping("/{groupId}/members/{userId}")
    public  ResponseEntity<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        var removeMemberCommand = new RemoveMemberCommand(groupId, userId);
        groupMemberCommandService.handle(removeMemberCommand);
        return ResponseEntity.noContent().build();
    }



    @Operation(summary = "Get all members of a group")
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberResource>> getGroupMembers(@PathVariable Long groupId) {
        var getAllMembersInGroupQuery = new GetAllMembersInGroupQuery(groupId);
        var members = groupMemberQueryService.handle(getAllMembersInGroupQuery);

        if (members.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        var memberResources = members.stream()
                .map(GroupMemberResourceFromEntityAssembler::fromEntityToResource)
                .collect(Collectors.toList());
        return ResponseEntity.ok(memberResources);
    }

    @PostMapping("/{groupId}/join")
    public ResponseEntity<GroupMemberResource> joinGroupWithToken(
            @PathVariable Long groupId,
            @RequestBody JoinGroupWithTokenResource joinGroupWithTokenResource) {

        var command = new JoinGroupWithTokenCommand(groupId, joinGroupWithTokenResource.token(), joinGroupWithTokenResource.userId());
        var newMember = groupMemberCommandService.handle(command);
        if (newMember.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); // Or handle as an error
        }
        var memberResource = GroupMemberResourceFromEntityAssembler.fromEntityToResource(newMember.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(memberResource);
    }

    @PostMapping("/join")
    public ResponseEntity<GroupMemberResource> joinGroupWithToken(
            @RequestBody JoinGroupWithTokenResource joinGroupWithTokenResource) {

        var command = new JoinGroupWithTokenCommand(null, joinGroupWithTokenResource.token(), joinGroupWithTokenResource.userId());
        var newMember = groupMemberCommandService.handle(command);
        if (newMember.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        var memberResource = GroupMemberResourceFromEntityAssembler.fromEntityToResource(newMember.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(memberResource);
    }

}

package com.pocketpeers.backend.users.interfaces.rest;

import com.pocketpeers.backend.users.domain.model.commands.DeleteUserInformationCommand;
import com.pocketpeers.backend.users.domain.model.queries.GetAllUsersInformationQuery;
import com.pocketpeers.backend.users.domain.model.queries.GetUserInformationByIdQuery;
import com.pocketpeers.backend.users.domain.model.queries.GetUserInformationByUserIdQuery;
import com.pocketpeers.backend.users.domain.services.SmtpService;
import com.pocketpeers.backend.users.domain.services.UserInformationCommandService;
import com.pocketpeers.backend.users.domain.services.UserInformationQueryService;
import com.pocketpeers.backend.users.interfaces.rest.resources.CreateUserInformationResource;
import com.pocketpeers.backend.users.interfaces.rest.resources.UpdateUserInformationResource;
import com.pocketpeers.backend.users.interfaces.rest.resources.UserInformationResource;
import com.pocketpeers.backend.users.interfaces.rest.transform.CreateUserInformationCommandFromResourceAssembler;
import com.pocketpeers.backend.users.interfaces.rest.transform.UpdateUserInformationCommandFromResourceAssembler;
import com.pocketpeers.backend.users.interfaces.rest.transform.UserInformationResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping(value = "/api/v1/usersInformation", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Users Information", description = "User Information Management Endpoints")
public class UsersInformationController {
    @Autowired
     private SmtpService emailService;
    private final UserInformationQueryService userInformationQueryService;
    private final UserInformationCommandService userInformationCommandService;

    public UsersInformationController(UserInformationQueryService userInformationQueryService, UserInformationCommandService userInformationCommandService) {
        this.userInformationQueryService = userInformationQueryService;
        this.userInformationCommandService = userInformationCommandService;
    }


    /*
    @Operation(summary = "Create a new user information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User information created successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @PostMapping
    public ResponseEntity<UserInformationResource> createUser(@RequestBody CreateUserInformationResource resource){
        var createUserCommand = CreateUserInformationCommandFromResourceAssembler.toCommandfromResource(resource);
        var user = userInformationCommandService.handle(createUserCommand);
        if (user.isEmpty()) return ResponseEntity.badRequest().build();
        var userResource = UserInformationResourceFromEntityAssembler.toResourceFromEntity(user.get());
//        try {
//            emailService.sendWelcomeEmail(userResource.email(), userResource.fullName());
//        } catch (MessagingException e) {
//            throw new RuntimeException(e);
//        }
        return new ResponseEntity<>(userResource, HttpStatus.CREATED);
    }
    */

    /*
    @Operation(summary = "Get user information by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information found"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "User information not found")
    })
    @GetMapping("/{userInformationId}")
    public ResponseEntity<UserInformationResource> getProfileById(@PathVariable Long userInformationId) {
        var getUserInformationByIdQuery = new GetUserInformationByIdQuery(userInformationId);
        var userInformation = userInformationQueryService.handle(getUserInformationByIdQuery);
        if (userInformation.isEmpty()) return ResponseEntity.badRequest().build();
        var profileResource = UserInformationResourceFromEntityAssembler.toResourceFromEntity(userInformation.get());
        return ResponseEntity.ok(profileResource);
    }
    */

    @Operation(summary = "Get user information by User ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information found"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "User information not found")
    })
    @GetMapping("/userId/{userId}")
    public ResponseEntity<UserInformationResource> getProfileByUserId(@PathVariable Long userId) {
        var getUserInformationByUserIdQuery = new GetUserInformationByUserIdQuery(userId);
        var userInformation = userInformationQueryService.handle(getUserInformationByUserIdQuery);
        if (userInformation.isEmpty()) return ResponseEntity.badRequest().build();
        var profileResource = UserInformationResourceFromEntityAssembler.toResourceFromEntity(userInformation.get());
        return ResponseEntity.ok(profileResource);
    }

    @Operation(summary = "Get my user information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information found"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "User information not found")
    })
    @GetMapping("/user")
    public ResponseEntity<UserInformationResource> getMyProfile(Authentication authentication) {
        String username = authentication.getName();
        var userInformation = userInformationQueryService.getByUsername(username);
        if (userInformation.isEmpty()) return ResponseEntity.badRequest().build();
        var profileResource = UserInformationResourceFromEntityAssembler.toResourceFromEntity(userInformation.get());
        return ResponseEntity.ok(profileResource);
    }

    /*
    @Operation(summary = "Get all users information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of users information")
    })
    @GetMapping
    public ResponseEntity<List<UserInformationResource>> getAllUsersInformation() {
        var getAllUsersInformationQuery = new GetAllUsersInformationQuery();
        var usersInformation = userInformationQueryService.handle(getAllUsersInformationQuery);
        var userResources = usersInformation.stream().map(UserInformationResourceFromEntityAssembler::toResourceFromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(userResources);
    }
*/

    @Operation(summary = "Update user information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information updated successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "User information not found")
    })
    @PutMapping("/user")
    public ResponseEntity<UserInformationResource> updateUserInformationById(@RequestBody UpdateUserInformationResource resource, Authentication authentication) {
        String username = authentication.getName();
        var userInformation = userInformationQueryService.getByUsername(username);
        if (userInformation.isEmpty()) return ResponseEntity.notFound().build();
        var updateUserCommand = UpdateUserInformationCommandFromResourceAssembler.toCommandfromResource(userInformation.get().getId(), resource);
        var updatedUserInformation = userInformationCommandService.handle(updateUserCommand);
        if (updatedUserInformation.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        var userInformationResource = UserInformationResourceFromEntityAssembler.toResourceFromEntity(updatedUserInformation.get());
        return ResponseEntity.ok(userInformationResource);
    }



    @Operation(summary = "Delete user information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User information deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "User information not found")
    })
    @DeleteMapping("/user")
    public ResponseEntity<Void> deleteUserInformationById(Authentication authentication) {
        String username = authentication.getName();
        var userInformation = userInformationQueryService.getByUsername(username);
        if (userInformation.isEmpty()) return ResponseEntity.notFound().build();
        var deleteUserInformationCommand = new DeleteUserInformationCommand(userInformation.get().getId());
        var userInformationDeleted = userInformationCommandService.handle(deleteUserInformationCommand);
        if (userInformationDeleted.isEmpty()) return ResponseEntity.badRequest().build();
        return ResponseEntity.noContent().build();
    }

}

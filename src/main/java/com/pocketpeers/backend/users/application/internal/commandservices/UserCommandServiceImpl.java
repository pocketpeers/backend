package com.pocketpeers.backend.users.application.internal.commandservices;

import com.pocketpeers.backend.users.application.internal.outboundservices.hashing.HashingService;
import com.pocketpeers.backend.users.application.internal.outboundservices.tokens.TokenService;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.commands.CreateUserInformationCommand;
import com.pocketpeers.backend.users.domain.model.commands.DeleteUserCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignInCommand;
import com.pocketpeers.backend.users.domain.model.commands.SignUpCommand;
import com.pocketpeers.backend.users.domain.model.queries.GetUserInformationByUserIdQuery;
import com.pocketpeers.backend.users.domain.services.UserCommandService;
import com.pocketpeers.backend.users.domain.services.UserInformationCommandService;
import com.pocketpeers.backend.users.domain.services.UserInformationQueryService;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.RoleRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserCommandServiceImpl implements UserCommandService {

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final TokenService tokenService;
    private final UserInformationCommandService userInformationCommandService;
    private final UserInformationQueryService userInformationQueryService;

    private final RoleRepository roleRepository;

    public UserCommandServiceImpl(UserRepository userRepository, HashingService hashingService, TokenService tokenService,
                                  RoleRepository roleRepository, UserInformationCommandService userInformationCommandService,
                                  UserInformationQueryService userInformationQueryService) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.tokenService = tokenService;
        this.roleRepository = roleRepository;
        this.userInformationCommandService = userInformationCommandService;
        this.userInformationQueryService = userInformationQueryService;
    }
  
    /**
     * Handles the deletion of a user.
     * @param command DeleteUserCommand object containing the user's ID.
     * @return Optional<User> Deleted user.
     */
    @Override
    public Optional<User> handle(DeleteUserCommand command) {
        var user = userRepository.findById(command.userId());
        user.ifPresent(userRepository::delete);
        return user;
    }
  
    /**
     * Handle the sign-in command
     * <p>
     *     This method handles the {@link SignInCommand} command and returns the user and the token.
     * </p>
     * @param command the sign-in command containing the username and password
     * @return and optional containing the user matching the username and the generated token
     * @throws RuntimeException if the user is not found or the password is invalid
     */
    @Override
    public Optional<ImmutablePair<User, String>> handle(SignInCommand command) {
        var user = userRepository.findByUsername(command.username());
        if (user.isEmpty())
            throw new RuntimeException("User not found");
        if (!hashingService.matches(command.password(), user.get().getPassword()))
            throw new RuntimeException("Invalid password");
        var query = new GetUserInformationByUserIdQuery(user.get().getId());
        var userInfo = userInformationQueryService.handle(query);
        if (userInfo.isEmpty())
            throw new RuntimeException("User information not found");
        // obtener rol
        var roles = user.get().getRoles();
        var role  = roles.isEmpty() ? "USER" : roles.stream().findFirst().get().getStringName();
        var token = tokenService.generateToken(user.get().getUsername(), role, userInfo.get().getFullName(),
                userInfo.get().getPhoneNumber(), userInfo.get().getPhoto(), userInfo.get().getEmail().email());
        return Optional.of(ImmutablePair.of(user.get(), token));
    }

    /**
     * Handles the updating of a user's details.
     * @param command UpdateUserCommand object containing the user's updated details.
     * @return Optional<User> Updated user.
     */
    @Override
    public Optional<User> handle(SignUpCommand command) {
        if (userRepository.existsByUsername(command.username()))
            throw new RuntimeException("Username already exists");
        var roles = command.roles().stream().map(role -> roleRepository.findByName(role.getName()).orElseThrow(
                () -> new RuntimeException("Role not found")
        )).toList();
        var user = new User(command.username(), hashingService.encode(command.password()), roles);
        var savedUser = userRepository.save(user);
        CreateUserInformationCommand userInformationCommand = new CreateUserInformationCommand(command.firstName(), command.lastName(), command.phoneNumber(), command.photo(), command.email(), savedUser.getId());
        userInformationCommandService.handle(userInformationCommand);
        return Optional.of(user);
    }

}

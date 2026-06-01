package com.pocketpeers.backend.users.application.internal.queryservices;

import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import com.pocketpeers.backend.users.domain.model.queries.GetAllUsersInformationQuery;
import com.pocketpeers.backend.users.domain.model.queries.GetUserInformationByIdQuery;
import com.pocketpeers.backend.users.domain.model.queries.GetUserInformationByUserIdQuery;
import com.pocketpeers.backend.users.domain.services.UserInformationQueryService;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserInformationRepository;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserInformationQueryServiceImpl implements UserInformationQueryService {

    private final UserInformationRepository userInformationRepository;
    private final UserRepository userRepository;

    public UserInformationQueryServiceImpl(UserInformationRepository userInformationRepository, UserRepository userRepository) {
        this.userInformationRepository = userInformationRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<UserInformation> handle(GetAllUsersInformationQuery query) {
        return userInformationRepository.findAll();
    }

    @Override
    public Optional<UserInformation> handle(GetUserInformationByIdQuery query) {
        return userInformationRepository.findById(query.userId());
    }

    @Override
    public Optional<UserInformation> handle(GetUserInformationByUserIdQuery query) {
        return userInformationRepository.findByUserId(query.id());
    }

    @Override
    public Optional<UserInformation> getByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
        return userInformationRepository.findByUserId(user.getId());
    }
}

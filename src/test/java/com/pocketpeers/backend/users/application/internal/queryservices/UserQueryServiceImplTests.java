package com.pocketpeers.backend.users.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.queries.GetAllUsersQuery;
import com.pocketpeers.backend.users.domain.model.queries.GetUserByIdQuery;
import com.pocketpeers.backend.users.domain.model.queries.GetUserByUsernameQuery;
import com.pocketpeers.backend.users.infrastructure.persistence.jpa.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceImplTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserQueryServiceImpl service;

    @Test
    void delegatesQueriesToRepository() {
        User user = new User("ana", "secret");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(user));

        assertThat(service.handle(new GetAllUsersQuery())).containsExactly(user);
        assertThat(service.handle(new GetUserByIdQuery(1L))).contains(user);
        assertThat(service.handle(new GetUserByUsernameQuery("ana"))).contains(user);
    }
}

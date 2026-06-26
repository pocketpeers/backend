package com.pocketpeers.backend.groups.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsByUserIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetAllGroupsQuery;
import com.pocketpeers.backend.groups.domain.model.queries.GetGroupByIdQuery;
import com.pocketpeers.backend.groups.domain.model.queries.SearchGroupsByNameQuery;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupMemberRepository;
import com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupQueryServiceImplTests {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    @SuppressWarnings("unused")
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private GroupQueryServiceImpl service;

    @Test
    void delegatesFindByIdAllAndUserGroups() {
        Group group = new Group("Casa", "Gastos", "photo.png");
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupRepository.findAll()).thenReturn(List.of(group));
        when(groupRepository.findAllByUserId(2L)).thenReturn(List.of(group));

        assertThat(service.handle(new GetGroupByIdQuery(1L))).contains(group);
        assertThat(service.handle(new GetAllGroupsQuery())).containsExactly(group);
        assertThat(service.handle(new GetAllGroupsByUserIdQuery(2L))).containsExactly(group);
    }

    @Test
    void searchReturnsEmptyForBlankAndTrimsName() {
        Group group = new Group("Casa", "Gastos", "photo.png");
        when(groupRepository.findAllByNameContainingIgnoreCase("Casa")).thenReturn(List.of(group));

        assertThat(service.handle(new SearchGroupsByNameQuery(" "))).isEmpty();
        assertThat(service.handle(new SearchGroupsByNameQuery(" Casa "))).containsExactly(group);
        verify(groupRepository).findAllByNameContainingIgnoreCase("Casa");
    }
}

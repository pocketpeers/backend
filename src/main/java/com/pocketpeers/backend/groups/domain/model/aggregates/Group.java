package com.pocketpeers.backend.groups.domain.model.aggregates;

import com.pocketpeers.backend.groups.domain.model.commands.CreateGroupCommand;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.valueobjects.InvitationToken;
import com.pocketpeers.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * The Group class represents a group entity in the system.
 * It extends the AuditableAbstractAggregateRoot class, which provides audit fields for the entity.
 */
@Getter
@Entity
@Table(name = "pocket_groups")
public class Group extends AuditableAbstractAggregateRoot<Group> {


    private String name;
    private String groupPhoto;
    private String description;
    private String invitationToken;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupMember> members = new ArrayList<>();



    public Group() {
        this.name = Strings.EMPTY;
        this.description = Strings.EMPTY;
        this.groupPhoto = Strings.EMPTY;
        this.invitationToken = Strings.EMPTY;

    }

    public Group(String name, String description, String photo) {
        this();
        this.name = name;
        this.description = description;
        this.groupPhoto = photo;
    }

    public Group(CreateGroupCommand command) {
        this();
        this.name = command.name();
        this.description = command.description();
        this.groupPhoto = command.groupPhoto();

    }

    public void changePhoto(String photo) {
        this.groupPhoto = photo;
    }

    public Group updateInformation(String name, String description) {
        this.name = name;
        this.description = description;
        return this;
    }

    public void generateInvitationToken() {
        this.invitationToken = new InvitationToken().getToken();
    }

    public boolean hasValidInvitationToken(String token) {
        return token.equals(this.invitationToken);
    }

    public void addMember(GroupMember member) {
        member.setGroup(this);
        this.members.add(member);
    }

    public Long getAdminId() {
        return members.stream()
                .filter(GroupMember::isAdmin)
                .findFirst()
                .map(GroupMember::getIdAdmin)
                .orElse(null);
    }



}

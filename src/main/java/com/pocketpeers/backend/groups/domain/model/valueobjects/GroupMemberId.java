package com.pocketpeers.backend.groups.domain.model.valueobjects;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

public class GroupMemberId implements Serializable {
    
    @Setter
    @Getter
    private Long group;
    private Long userInformation;

    
    public GroupMemberId() {}

    public Long getUser() {
        return userInformation;
    }

    public void setUser(Long userInformation) {
        this.userInformation = userInformation;
    }

    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupMemberId that = (GroupMemberId) o;
        return Objects.equals(group, that.group) && Objects.equals(userInformation, that.userInformation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, userInformation);
    }
}
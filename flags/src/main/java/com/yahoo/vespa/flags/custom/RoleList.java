// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class RoleList {

    @JsonProperty("roles")
    List<Role> roles;

    @JsonCreator
    public RoleList(@JsonProperty("roles") List<Role> roles) {
        this.roles = roles;
    }

    public List<Role> roles() {
        return roles;
    }

    public static RoleList empty() {
        return new RoleList(List.of());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoleList roleList = (RoleList) o;
        return Objects.equals(roles, roleList.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roles);
    }
}

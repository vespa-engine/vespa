// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms;

import com.yahoo.vespa.athenz.api.AthenzRole;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class RoleAction {
    private final String roleName;
    private final String action;

    public RoleAction(String roleName, String action) {
        this.roleName = roleName;
        this.action = action;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "RoleAction{" +
                "roleName=" + roleName +
                ", action='" + action + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoleAction that = (RoleAction) o;
        return Objects.equals(roleName, that.roleName) &&
                Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleName, action);
    }
}

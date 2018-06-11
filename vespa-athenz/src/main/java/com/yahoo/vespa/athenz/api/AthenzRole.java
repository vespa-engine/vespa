// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.Objects;

/**
 * @author tokle
 */
public class AthenzRole {
    private final AthenzDomain domain;
    private final String roleName;

    public AthenzRole(AthenzDomain domain, String roleName) {
        this.domain = domain;
        this.roleName = roleName;
    }

    public AthenzDomain domain() {
        return domain;
    }

    public String roleName() {
        return roleName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzRole that = (AthenzRole) o;
        return Objects.equals(domain, that.domain) &&
               Objects.equals(roleName, that.roleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, roleName);
    }

    @Override
    public String toString() {
        return "AthenzRole{" +
               "domain=" + domain +
               ", roleName='" + roleName + '\'' +
               '}';
    }
}

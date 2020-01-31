// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.Objects;

/**
 * @author tokle
 */
public class AthenzRole {
    private static final String ROLE_RESOURCE_PREFIX = "role.";

    private final AthenzDomain domain;
    private final String roleName;

    public AthenzRole(AthenzDomain domain, String roleName) {
        this.domain = domain;
        this.roleName = roleName;
    }

    public AthenzRole(String domain, String roleName) {
        this.domain = new AthenzDomain(domain);
        this.roleName = roleName;
    }

    public static AthenzRole fromResourceNameString(String string) {
        return fromResourceName(AthenzResourceName.fromString(string));
    }

    public static AthenzRole fromResourceName(AthenzResourceName resourceName) {
        String entityName = resourceName.getEntityName();
        if (!entityName.startsWith(ROLE_RESOURCE_PREFIX)) {
            throw new IllegalArgumentException("Not a valid role: " + resourceName.toResourceNameString());
        }
        String roleName = entityName.substring(ROLE_RESOURCE_PREFIX.length());
        return new AthenzRole(resourceName.getDomain(), roleName);
    }

    public AthenzDomain domain() {
        return domain;
    }

    public String roleName() {
        return roleName;
    }

    public String toResourceNameString() { return toResourceName().toResourceNameString(); }

    public AthenzResourceName toResourceName() { return new AthenzResourceName(domain, ROLE_RESOURCE_PREFIX + roleName); }

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

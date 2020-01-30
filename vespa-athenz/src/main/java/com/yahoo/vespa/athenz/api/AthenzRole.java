// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.Objects;

/**
 * @author tokle
 */
public class AthenzRole {
    private static final String DOMAIN_ROLE_NAME_DELIMITER = ":role.";

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

    public static AthenzRole fromString(String string) {
        if (!string.contains(DOMAIN_ROLE_NAME_DELIMITER)) {
            throw new IllegalArgumentException("Not a valid role: " + string);
        }
        int delimiterIndex = string.indexOf(DOMAIN_ROLE_NAME_DELIMITER);
        String domain = string.substring(0, delimiterIndex);
        String roleName = string.substring(delimiterIndex + DOMAIN_ROLE_NAME_DELIMITER.length());
        return new AthenzRole(domain, roleName);
    }

    public AthenzDomain domain() {
        return domain;
    }

    public String roleName() {
        return roleName;
    }

    public String asString() { return domain.getName() + DOMAIN_ROLE_NAME_DELIMITER + roleName; }

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

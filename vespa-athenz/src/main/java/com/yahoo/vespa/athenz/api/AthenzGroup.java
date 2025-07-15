// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.athenz.api;

import java.util.Objects;

public class AthenzGroup implements AthenzIdentity {
    private final AthenzDomain domain;
    private final String groupName;

    public AthenzGroup(AthenzDomain domain, String groupName) {
        this.domain = domain;
        this.groupName = groupName;
    }

    public AthenzGroup(String domain, String groupName) {
        this.domain = new AthenzDomain(domain);
        this.groupName = groupName;
    }

    public AthenzDomain domain() {
        return domain;
    }

    public String groupName() {
        return groupName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzGroup that = (AthenzGroup) o;
        return Objects.equals(domain, that.domain) && Objects.equals(groupName, that.groupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, groupName);
    }

    @Override
    public AthenzDomain getDomain() {
        return domain;
    }

    @Override
    public String getName() {
        return groupName;
    }

    @Override
    public String getFullName() {
        return getDomainName() + ":group." + getName();
    }
}

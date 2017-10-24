// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

import java.security.Principal;
import java.util.Objects;

/**
 * @author bjorncs
 */
public class AthenzPrincipal implements Principal {

    private final AthenzDomain domain;
    private final UserId userId;

    public AthenzPrincipal(AthenzDomain domain, UserId userId) {
        this.domain = domain;
        this.userId = userId;
    }

    public UserId getUserId() {
        return userId;
    }

    public AthenzDomain getDomain() {
        return domain;
    }

    public String toYRN() {
        return domain.id() + "." + userId.id();
    }

    @Override
    public String getName() {
        return userId.id();
    }

    @Override
    public String toString() {
        return "AthenzPrincipal{" +
                "domain=" + domain +
                ", userId=" + userId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzPrincipal that = (AthenzPrincipal) o;
        return Objects.equals(domain, that.domain) &&
                Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, userId);
    }

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import com.yahoo.vespa.athenz.utils.AthenzIdentities;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class AthenzUser implements AthenzIdentity {
    private final String userId;

    public AthenzUser(String userId) {
        this.userId = userId;
    }

    public static AthenzUser fromUserId(String userId) {
        return new AthenzUser(userId);
    }

    @Override
    public AthenzDomain getDomain() {
        return AthenzIdentities.USER_PRINCIPAL_DOMAIN;
    }

    @Override
    public String getName() {
        return userId;
    }

    @Override
    public String toString() {
        return "AthenzUser{" +
                "userId=" + userId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzUser that = (AthenzUser) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}

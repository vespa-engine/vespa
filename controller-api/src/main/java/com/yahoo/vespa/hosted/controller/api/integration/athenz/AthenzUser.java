// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class AthenzUser implements AthenzIdentity {
    private final UserId userId;

    public AthenzUser(UserId userId) {
        this.userId = userId;
    }

    public static AthenzUser fromUserId(UserId userId) {
        return new AthenzUser(userId);
    }

    @Override
    public AthenzDomain getDomain() {
        return AthenzUtils.USER_PRINCIPAL_DOMAIN;
    }

    @Override
    public String getName() {
        return userId.id();
    }

    public UserId getUserId() {
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

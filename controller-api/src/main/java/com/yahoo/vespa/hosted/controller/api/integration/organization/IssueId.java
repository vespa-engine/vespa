// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import java.util.Objects;

/**
 * Used to identify issues stored in some issue tracking system.
 * The {@code value()} and {@code from()} methods should be inverses.
 *
 * @author jonmv
 */
public class IssueId {

    protected final String id;

    protected IssueId(String id) {
        this.id = id;
    }

    public static IssueId from(String value) {
        if (value.isEmpty())
            throw new IllegalArgumentException("Can not make an IssueId from an empty value.");

        return new IssueId(value);
    }

    public String value() {
        return id;
    }

    @Override
    public String toString() {
        return value();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IssueId issueId = (IssueId) o;
        return Objects.equals(id, issueId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.text.Utf8String;

/**
 * An id which is unique across the cluster of nodes
 *
 * @author baldersheim
 */
public class SessionId {

    private final Utf8String id;

    public SessionId(UniqueRequestId requestId, String localSessionId) {
        this.id = new Utf8String(requestId.toString() + "." + localSessionId);
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public Utf8String asUtf8String() { return id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SessionId sessionId = (SessionId) o;

        return id.equals(sessionId.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

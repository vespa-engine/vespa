// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.text.Utf8String;

/**
 * A id which is unique across this cluster + the extra differentiator.
 *
 * @author baldersheim
 */
public class SessionId {

    private final Utf8String id;

    public SessionId(UniqueRequestId requestId, String extraDifferentiator) {
        this.id = new Utf8String(requestId.toString() + "." + extraDifferentiator);
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

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.text.Utf8String;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A query id which is unique across this cluster - consisting of
 * container runtime id + timestamp + serial.
 *
 * @author bratseth
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
}

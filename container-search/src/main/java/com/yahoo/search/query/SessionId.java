// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.container.Server;
import com.yahoo.text.Utf8String;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A query id which is unique across this cluster - consisting of
 * container runtime id + timestamp + serial.
 *
 * @author bratseth
 */
public class SessionId {

    private static final String serverId = Server.get().getServerDiscriminator();
    private static final AtomicLong sequenceCounter = new AtomicLong();

    private final Utf8String id;

    private SessionId(String serverId, long timestamp, long sequence) {
        this.id = new Utf8String(serverId + "." + timestamp + "." + sequence);
    }

    public Utf8String asUtf8String() { return id; }

    /**
     * Creates a session id which is unique across the cluster this runtime is a member of each time this is called.
     * Calling this causes synchronization.
     */
    public static SessionId next() {
        return new SessionId(serverId, System.currentTimeMillis(), sequenceCounter.getAndIncrement());
    }

}

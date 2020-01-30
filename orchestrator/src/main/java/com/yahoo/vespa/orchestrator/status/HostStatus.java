// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

/**
 * Enumeration of the different statuses a host can have.
 *
 * @author oyving
 */
public enum HostStatus {
    /** The services on the host is supposed to be up. */
    NO_REMARKS(false),

    /** The services on the host is allowed to be down. */
    ALLOWED_TO_BE_DOWN(true),

    /**
     * Same as ALLOWED_TO_BE_DOWN, but in addition, it is expected
     * the host may be removed from its application at any moment.
     */
    PERMANENTLY_DOWN(true);

    private final boolean suspended;

    HostStatus(boolean suspended) { this.suspended = suspended; }
    public boolean isSuspended() { return suspended; }
    public String asString() { return name(); }
}

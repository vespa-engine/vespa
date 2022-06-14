// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * A maintenance event for a host.
 *
 * @author mpolden
 */
public class HostEvent {

    private final String id;
    private final String hostId;
    private final String description;

    public HostEvent(String id, String hostId, String description) {
        this.id = Objects.requireNonNull(id);
        this.hostId = Objects.requireNonNull(hostId);
        this.description = Objects.requireNonNull(description);
    }

    /** ID of the event */
    public String id() {
        return id;
    }

    /** ID of the host affected by this event, i.e. instance ID */
    public String hostId() {
        return hostId;
    }

    /** Human-readable description of the event */
    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return "event " + id + " affecting host " + hostId + ": '" + description + "'";
    }

}

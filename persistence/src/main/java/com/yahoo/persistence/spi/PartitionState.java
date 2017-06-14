// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi;

/**
* @author thomasg
*/
public class PartitionState {
    public PartitionState(State state, String reason) {
        this.state = state;
        this.reason = reason;

        if (reason == null || state == null) {
            throw new IllegalArgumentException("State and reason must be non-null");
        }
    }

    public State getState() { return state; }
    public String getReason() { return reason; }

    State state;
    String reason;

    public enum State {
        UP,
        DOWN
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

public class ClusterEvent implements Event{

    enum Type {
        SYSTEMSTATE,
        MASTER_ELECTION
    }

    private final String description;
    private long timeMs = 0;
    private final Type type;

    public ClusterEvent(Type type, String description, long timeMs) {
        this.type = type;
        this.description = description;
        this.timeMs = timeMs;
    }

    @Override
    public long getTimeMs() {
        return timeMs;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Cluster event type " + type + " @" + timeMs + ": " + description;
    }

    @Override
    public String getCategory() {
        return type.toString();
    }

}

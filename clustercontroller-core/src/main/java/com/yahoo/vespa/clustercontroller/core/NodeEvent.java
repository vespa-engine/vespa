// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Optional;

public class NodeEvent implements Event {

    private final NodeInfo node;
    private final String description;
    private final long eventTime;
    private final Optional<String> bucketSpace;

    public enum Type {
        REPORTED,
        CURRENT,
        WANTED
    }

    private final Type type;

    private NodeEvent(NodeInfo node, String description, Type type, long currentTime) {
        this.node = node;
        this.description = description;
        this.eventTime = currentTime;
        this.type = type;
        this.bucketSpace = Optional.empty();
    }

    private NodeEvent(NodeInfo node, String bucketSpace, String description, Type type, long currentTime) {
        this.node = node;
        this.description = description;
        this.eventTime = currentTime;
        this.type = type;
        this.bucketSpace = Optional.of(bucketSpace);
    }

    public static NodeEvent forBaseline(NodeInfo node, String description, Type type, long currentTime) {
        return new NodeEvent(node, description, type, currentTime);
    }

    public static NodeEvent forBucketSpace(NodeInfo node, String bucketSpace, String description, Type type, long currentTime) {
        return new NodeEvent(node, bucketSpace, description, type, currentTime);
    }

    public NodeInfo getNode() {
        return node;
    }

    @Override
    public long getTimeMs() {
        return eventTime;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Event: " + getNodeBucketSpaceDescription() + ": " + description;
    }

    private String getNodeBucketSpaceDescription() {
        if (bucketSpace.isPresent()) {
            return node.getNode() + " (" + bucketSpace.get() + ")";
        }
        return node.getNode().toString();
    }

    @Override
    public String getCategory() {
        return type.toString();
    }

    public Type getType() {
        return type;
    }

    public Optional<String> getBucketSpace() {
        return bucketSpace;
    }

}

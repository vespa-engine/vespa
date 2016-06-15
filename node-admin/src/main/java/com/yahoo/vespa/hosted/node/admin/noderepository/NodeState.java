// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository;

// TODO: Unite with com.yahoo.vespa.hosted.provision.Node.State
public enum NodeState {
    PROVISIONED, READY, RESERVED, ACTIVE, INACTIVE, DIRTY, FAILED
}

// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

public class NodeRepositoryException extends RuntimeException {
    public NodeRepositoryException(String message) {
        super(message);
    }

    public NodeRepositoryException(String message, Exception exception) {
        super(message, exception);
    }
}

// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

public class NoSuchNodeException extends NodeRepositoryException {
    public NoSuchNodeException(String message) {
        super(message);
    }
}

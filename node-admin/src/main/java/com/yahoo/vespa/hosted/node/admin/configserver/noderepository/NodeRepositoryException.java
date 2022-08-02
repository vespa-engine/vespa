// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;

public class NodeRepositoryException extends ConvergenceException {
    public NodeRepositoryException(String message) {
        super(message, null, true);
    }
}

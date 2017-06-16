// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

@SuppressWarnings("serial")
public class ConvergenceException extends RuntimeException {
    public ConvergenceException(String message) {
        super(message);
    }
}

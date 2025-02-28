// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * Exception thrown when node(s) selected in prepare() are not found
 * during activate(). Happens when the host(s) fail to come up and are
 * eventually removed with their nodes. The receiver must redo prepare().
 * 
 * @author freva
 *
 */
public class RequestedNodesMissingException extends TransientException {

    public RequestedNodesMissingException(String message) {
        super(message);
    }

}

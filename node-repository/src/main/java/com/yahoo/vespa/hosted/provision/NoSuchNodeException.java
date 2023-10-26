// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

/**
 * Thrown when a node is not found
 *
 * @author musum
 */
public class NoSuchNodeException extends IllegalArgumentException {

    public NoSuchNodeException(String message) { super(message); }

    public NoSuchNodeException(String message, Throwable cause) { super(message, cause); }

}

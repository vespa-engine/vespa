// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

/**
 * Thrown when a resource is not found
 *
 * @author musum
 */
public class NoSuchNodeException extends RuntimeException {

    public NoSuchNodeException(String message) { super(message); }

}

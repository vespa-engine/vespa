// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

/**
 * @author hmusum
 */
public class InvalidApplicationException extends IllegalArgumentException {

    public InvalidApplicationException(String message) { super(message); }

    public InvalidApplicationException(Throwable t) { super(t); }

    public InvalidApplicationException(String message, Throwable e) {
        super(message, e);
    }

}

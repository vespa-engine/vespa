// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

/**
 * @author Vegard Sjonfjell
 */
public final class VersionDoesNotExistException extends RuntimeException {

    public VersionDoesNotExistException(String message) {
        super(message);
    }

}

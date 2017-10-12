// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz;

/**
 * @author bjorncs
 */
public class InvalidTokenException extends Exception {
    public InvalidTokenException(String message) {
        super(message);
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

/**
 * @author hakonhall
 */
public class FlagValidationException extends RuntimeException {
    public FlagValidationException(String message) {
        super(message);
    }
}

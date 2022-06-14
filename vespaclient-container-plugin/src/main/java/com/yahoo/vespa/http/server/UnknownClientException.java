// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

/**
 * @author Einar M R Rosenvinge
 */
public class UnknownClientException extends RuntimeException {

    public UnknownClientException(String message) {
        super(message);
    }

}

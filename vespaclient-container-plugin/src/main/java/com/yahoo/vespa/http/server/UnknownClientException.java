// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.5.0
 */
public class UnknownClientException extends RuntimeException {

    public UnknownClientException(String message) {
        super(message);
    }

}

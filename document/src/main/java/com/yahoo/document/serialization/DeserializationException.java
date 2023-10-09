// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

/**
 * Exception which is thrown when deserialization fails.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DeserializationException extends RuntimeException {
    public DeserializationException(String msg) {
        super(msg);
    }

    public DeserializationException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public DeserializationException(Throwable cause) {
        super(cause);
    }
}

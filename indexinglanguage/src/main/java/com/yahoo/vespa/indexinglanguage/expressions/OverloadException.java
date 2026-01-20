// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

/**
 * Thrown during indexing expression execution when overloaded or rate-limited.
 *
 * @author bjorncs
 */
public class OverloadException extends RuntimeException {

    public OverloadException(String message) {
        super(message);
    }

    public OverloadException(String message, Throwable cause) {
        super(message, cause);
    }
}

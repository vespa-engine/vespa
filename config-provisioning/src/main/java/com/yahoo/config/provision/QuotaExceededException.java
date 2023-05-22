// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * @author hmusum
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(Throwable t) {
        super(t);
    }

    public QuotaExceededException(String message) {
        super(message);
    }

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 *
 * Exception thrown when we are unable to get the Zookeeper application lock.
 * @author hmusum
 *
 */
public class ApplicationLockException extends RuntimeException {

    public ApplicationLockException(Exception e) {
        super(e);
    }

    public ApplicationLockException(String message) {
        super(message);
    }

}

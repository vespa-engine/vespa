// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 *
 * Exception thrown when we are unable to fulfill the request due to
 * having too few nodes (of the specified flavor)
 * 
 * @author hmusum
 */
public class OutOfCapacityException extends RuntimeException {

    public OutOfCapacityException(String message) {
        super(message);
    }

}

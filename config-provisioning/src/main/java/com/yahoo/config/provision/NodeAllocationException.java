// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 *
 * Exception thrown when we are unable to fulfill a node allocation request.
 * 
 * @author hmusum
 */
public class NodeAllocationException extends RuntimeException {

    public NodeAllocationException(String message) {
        super(message);
    }

}

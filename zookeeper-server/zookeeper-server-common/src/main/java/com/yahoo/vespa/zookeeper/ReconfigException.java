// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

/**
 * Interface for reconfiguring a zookeeper cluster.
 *
 * @author hmusum
 */
@SuppressWarnings("serial")
public class ReconfigException extends RuntimeException {

    public ReconfigException(Throwable cause) {
        super(cause);
    }

    public ReconfigException(String message) {
        super(message);
    }
}

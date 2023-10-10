// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

/**
 * Exception used to wrap zookeeper exception when reconfiguration fails, done to be able
 * to use a class that does not depend on ZooKeeper.
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

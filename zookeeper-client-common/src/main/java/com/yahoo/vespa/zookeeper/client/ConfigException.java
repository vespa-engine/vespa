// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper.client;

/**
 * Exception used to wrap zookeeper exception when clien configuration fails in a
 * class that can be used without depending on ZooKeeper.
 *
 * @author hmusum
 */
public class ConfigException extends RuntimeException {

    public ConfigException(Throwable cause) {
        super(cause);
    }

    public ConfigException(String message) {
        super(message);
    }

}

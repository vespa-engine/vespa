// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import java.util.List;

/**
 * @author hmusum
 */
public interface ConnectionPool extends AutoCloseable {

    void close();

    Connection getCurrent();

    /**
     * Switches to another (healthy, if possible) Connection instance. {@link #getCurrent()} will
     * return this instance afterwards, which is also the return value.
     *
     * @return a Connection
     */
    Connection switchConnection(Connection failingConnection);

    int getSize();

    List<Connection> connections();

}

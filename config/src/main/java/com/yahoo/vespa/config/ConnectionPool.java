// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Supervisor;

/**
 * @author hmusum
 */
public interface ConnectionPool extends AutoCloseable {

    void close();

    /**
     * Sets the supplied Connection to have an error, implementations are expected to call
     * {@link #switchConnection(Connection)} after setting state for the supplied Connection.
     *
     */
    void setError(Connection connection, int i);

    Connection getCurrent();

    /**
     * Switches to another (healthy, if one exists) Connection instance.
     * Returns the resulting Connection. See also {@link #setError(Connection, int)}
     *
     * @return a Connection
     */
    Connection switchConnection(Connection failingConnection);

    int getSize();

    // TODO: Exposes implementation, try to remove
    Supervisor getSupervisor();

}

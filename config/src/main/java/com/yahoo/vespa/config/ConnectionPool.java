// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Supervisor;

/**
 * @author hmusum
 */
public interface ConnectionPool {

    void close();

    /**
     * Sets the supplied Connection to have an error, implementations are expected to call
     * {@link #switchConnection()} after setting state for the supplied Connection.
     *
     */
    void setError(Connection connection, int i);

    Connection getCurrent();

    /**
     * Switches to another JRTConnection instance by randomly choosing
     * from the available sources, disregarding the current connection if there is
     * more than one source. Returns the resulting Connection. See also {@link #setError(Connection, int)}
     *
     * @return a JRTConnection
     */
    Connection switchConnection();

    /**
     * Sets the current JRTConnection instance by randomly choosing
     * from the available sources and returns the result.
     *
     * @return a JRTConnection
     */
    @Deprecated
    default Connection setNewCurrentConnection() { return switchConnection(); };

    int getSize();

    Supervisor getSupervisor();
}

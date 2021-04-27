// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Supervisor;

/**
 * @author hmusum
 */
public interface ConnectionPool extends AutoCloseable {

    void close();

    void setError(Connection connection, int i);

    Connection getCurrent();

    Connection setNewCurrentConnection();

    int getSize();

    Supervisor getSupervisor();
}

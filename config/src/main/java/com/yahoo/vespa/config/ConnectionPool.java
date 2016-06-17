// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

/**
 * @author hmusum
 */
public interface ConnectionPool {

    void close();

    void setError(Connection connection, int i);

    Connection getCurrent();

    Connection setNewCurrentConnection();

    int getSize();
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

/**
 * Represents a connection to a particular node (host/port).
 * Right now this is just a container of connection parameters, but might be extended to
 * contain an open connection later.
 * The host and port state is immutable.
 *
 * @author bratseth
 */
public class Connection {

    private String host;
    private int port;

    public Connection(String host,int port) {
        this.host=host;
        this.port=port;
    }

    public String getHost() { return host; }

    public int getPort() { return port; }

    public String toString() {
        return "http connection '" + host + ":" + port + "'";
    }

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.Collection;

/**
 * Contains information about a port (port number and a collection of tags).
 *
 */
public class PortInfo {
    private final int port;
    private final Collection<String> tags;

    public PortInfo(int port, Collection<String> tags) {
        this.port = port;
        this.tags = tags;
    }

    public int getPort() {
        return port;
    }

    public Collection<String> getTags() {
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PortInfo portInfo = (PortInfo) o;

        if (port != portInfo.port) return false;
        if (tags != null ? !tags.equals(portInfo.tags) : portInfo.tags != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = port;
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        return result;
    }
}

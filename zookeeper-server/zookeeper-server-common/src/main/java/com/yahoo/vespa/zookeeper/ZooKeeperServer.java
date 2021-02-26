// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import java.util.Objects;

/**
 * A ZooKeeper server and its ID.
 *
 * @author mpolden
 */
public class ZooKeeperServer {

    private final int id;
    private final String hostname;

    public ZooKeeperServer(int id, String hostname) {
        if (id < 0 || id > 255) throw new IllegalArgumentException("server id must be between 0 and 255");
        this.id = id;
        this.hostname = Objects.requireNonNull(hostname);
    }

    public int id() {
        return id;
    }

    public String hostname() {
        return hostname;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZooKeeperServer that = (ZooKeeperServer) o;
        return id == that.id && hostname.equals(that.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, hostname);
    }

    @Override
    public String toString() {
        return "server " + id + "=" + hostname;
    }
}

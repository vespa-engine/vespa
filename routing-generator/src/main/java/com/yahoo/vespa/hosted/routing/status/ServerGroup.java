// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.status;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A group of servers behind a router/reverse proxy.
 *
 * @author mpolden
 */
public class ServerGroup {

    private static final double requiredUpFraction = 0.25D;

    private final Map<String, List<Server>> servers;

    public ServerGroup(List<Server> servers) {
        this.servers = servers.stream().collect(Collectors.collectingAndThen(Collectors.groupingBy(Server::upstreamName),
                                                                             Collections::unmodifiableMap));
    }

    public Map<String, List<Server>> asMap() {
        return servers;
    }

    /** Returns whether given upstream is healthy */
    public boolean isHealthy(String upstreamName) {
        List<Server> upstreamServers = servers.getOrDefault(upstreamName, List.of());
        long upCount = upstreamServers.stream()
                                      .filter(Server::up)
                                      .count();
        return upCount > upstreamServers.size() * requiredUpFraction;
    }

    public static class Server {

        private final String upstreamName;
        private final String hostport;
        private final boolean up;

        public Server(String upstreamName, String hostport, boolean up) {
            this.upstreamName = upstreamName;
            this.hostport = hostport;
            this.up = up;
        }

        public String upstreamName() {
            return upstreamName;
        }

        public String hostport() {
            return hostport;
        }

        public boolean up() {
            return up;
        }

    }

}

// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;

import java.util.List;
import java.util.Objects;

/**
 * Information about a given OS version in the system.
 *
 * @author mpolden
 */
public class OsVersion {

    private final Version version;
    private final List<Node> nodes;

    public OsVersion(Version version, List<Node> nodes) {
        this.version = version;
        this.nodes = ImmutableList.copyOf(nodes);
    }

    /** The version number of this */
    public Version version() {
        return version;
    }

    /** Nodes on this version */
    public List<Node> nodes() {
        return nodes;
    }

    public static class Node {

        private final HostName hostname;
        private final Version version;
        private final Environment environment;
        private final RegionName region;

        public Node(HostName hostname, Version version, Environment environment, RegionName region) {
            this.hostname = hostname;
            this.version = version;
            this.environment = environment;
            this.region = region;
        }

        public HostName hostname() {
            return hostname;
        }

        public Version version() {
            return version;
        }

        public Environment environment() {
            return environment;
        }

        public RegionName region() {
            return region;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return Objects.equals(hostname, node.hostname) &&
                   Objects.equals(version, node.version) &&
                   environment == node.environment &&
                   Objects.equals(region, node.region);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, version, environment, region);
        }
    }

}

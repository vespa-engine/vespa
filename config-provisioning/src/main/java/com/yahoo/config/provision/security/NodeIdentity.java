// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.security;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;

import java.util.Objects;
import java.util.Optional;

/**
 * The identity of a Vespa node
 *
 * @author bjorncs
 */
public class NodeIdentity {

    private final NodeType nodeType;
    private final String identityName;
    private final HostName hostname;

    private NodeIdentity(NodeType nodeType, String identityName, HostName hostname) {
        this.nodeType = nodeType;
        this.identityName = identityName;
        this.hostname = hostname;
    }

    public NodeType nodeType() {
        return nodeType;
    }


    public Optional<String> identityName() {
        return Optional.ofNullable(identityName);
    }

    public Optional<HostName> hostname() {
        return Optional.ofNullable(hostname);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeIdentity that = (NodeIdentity) o;
        return nodeType == that.nodeType &&
                Objects.equals(identityName, that.identityName) &&
                Objects.equals(hostname, that.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeType, identityName, hostname);
    }

    @Override
    public String toString() {
        return "NodeIdentity{" +
                "nodeType=" + nodeType +
                ", identityName='" + identityName + '\'' +
                ", hostname=" + hostname +
                '}';
    }

    public static class Builder {
        private final NodeType nodeType;
        private String identityName;
        private HostName hostname;

        public Builder(NodeType nodeType) {
            this.nodeType = nodeType;
        }

        public Builder identityName(String identityName) {
            this.identityName = identityName;
            return this;
        }

        public Builder hostname(HostName hostname) {
            this.hostname = hostname;
            return this;
        }

        public NodeIdentity build() {
            return new NodeIdentity(nodeType, identityName, hostname);
        }
    }
}

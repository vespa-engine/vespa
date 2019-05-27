// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.security;

import com.yahoo.config.provision.ApplicationId;
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
    private final ApplicationId applicationId;
    private final Parent parentNode;

    private NodeIdentity(NodeType nodeType, String identityName, HostName hostname, ApplicationId applicationId, Parent parentNode) {
        this.nodeType = nodeType;
        this.identityName = identityName;
        this.hostname = hostname;
        this.applicationId = applicationId;
        this.parentNode = parentNode;
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

    public Optional<ApplicationId> applicationId() {
        return Optional.ofNullable(applicationId);
    }

    public Optional<Parent> parentNode() {
        return Optional.ofNullable(parentNode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeIdentity that = (NodeIdentity) o;
        return nodeType == that.nodeType &&
                Objects.equals(identityName, that.identityName) &&
                Objects.equals(hostname, that.hostname) &&
                Objects.equals(applicationId, that.applicationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeType, identityName, hostname, applicationId);
    }

    @Override
    public String toString() {
        return "NodeIdentity{" +
                "nodeType=" + nodeType +
                ", identityName='" + identityName + '\'' +
                ", hostname=" + hostname +
                ", applicationId=" + applicationId +
                '}';
    }

    public static class Builder {
        private final NodeType nodeType;
        private String identityName;
        private HostName hostname;
        private ApplicationId applicationId;
        private Parent parentNode;

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

        public Builder applicationId(ApplicationId applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        public Builder parentNode(NodeType nodeType, HostName hostname) {
            this.parentNode = new Parent(nodeType, hostname);
            return this;
        }

        public NodeIdentity build() {
            return new NodeIdentity(nodeType, identityName, hostname, applicationId, parentNode);
        }
    }

    public static class Parent {
        private final NodeType nodeType;
        private final HostName hostname;

        private Parent(NodeType nodeType, HostName hostname) {
            this.nodeType = nodeType;
            this.hostname = hostname;
        }

        public NodeType nodeType() {
            return nodeType;
        }

        public HostName hostname() {
            return hostname;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Parent parent = (Parent) o;
            return nodeType == parent.nodeType &&
                    Objects.equals(hostname, parent.hostname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeType, hostname);
        }

        @Override
        public String toString() {
            return "Parent{" +
                    "nodeType=" + nodeType +
                    ", hostname=" + hostname +
                    '}';
        }
    }
}

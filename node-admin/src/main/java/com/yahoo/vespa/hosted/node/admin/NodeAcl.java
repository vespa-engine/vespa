// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;

import java.util.Objects;

/**
 * An ACL specification for a container.
 *
 * @author mpolden
 */
public class NodeAcl {

    private final String hostname;
    private final String ipAddress;
    private final ContainerName trustedBy;

    public NodeAcl(String hostname, String ipAddress, ContainerName trustedBy) {
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.trustedBy = trustedBy;
    }

    public String hostname() {
        return hostname;
    }

    public String ipAddress() {
        return ipAddress;
    }

    public ContainerName trustedBy() {
        return trustedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeAcl that = (NodeAcl) o;
        return Objects.equals(hostname, that.hostname) &&
                Objects.equals(ipAddress, that.ipAddress) &&
                Objects.equals(trustedBy, that.trustedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, ipAddress, trustedBy);
    }
}

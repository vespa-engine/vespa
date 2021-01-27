// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.hostinfo.ResourceUsage;

import java.util.Objects;

/**
 * Wrapper that identifies a resource type that has been exhausted on a given node,
 * complete with both actual usage and the limit it exceeded.
 */
public class NodeResourceExhaustion {
    public final Node node;
    public final String resourceType;
    public final ResourceUsage resourceUsage;
    public final double limit;

    public NodeResourceExhaustion(Node node, String resourceType,
                                  ResourceUsage resourceUsage, double limit) {
        this.node = node;
        this.resourceType = resourceType;
        this.resourceUsage = resourceUsage;
        this.limit = limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeResourceExhaustion that = (NodeResourceExhaustion) o;
        return Double.compare(that.limit, limit) == 0 &&
                Objects.equals(node, that.node) &&
                Objects.equals(resourceType, that.resourceType) &&
                Objects.equals(resourceUsage, that.resourceUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, resourceType, resourceUsage, limit);
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.vespa.hosted.provision.Node;

import java.util.Optional;

/**
 * A node with additional information required to prioritize it for allocation.
 *
 * @author smorgrav
 */
class PrioritizableNode implements Comparable<PrioritizableNode> {

    Node node;

    /** The free capacity excluding headroom, including retired allocations */
    ResourceCapacity freeParentCapacity = new ResourceCapacity();

    /** The parent host (docker or hypervisor) */
    Optional<Node> parent = Optional.empty();

    /** True if the node is allocated to a host that should be dedicated as a spare */
    boolean violatesSpares;

    /** True if the node is (or would be) allocated on slots that should be dedicated to headroom */
    boolean violatesHeadroom;

    /** True if this is a node that has been retired earlier in the allocation process */
    boolean isSurplusNode;

    /** This node does not exist in the node repository yet */
    boolean isNewNode;

    /** True if exact flavor is specified by the allocation request and this node has this flavor */
    boolean preferredOnFlavor;

    /**
     * Compares two prioritizable nodes
     *
     * @return negative if first priority is higher than second node
     */
    @Override
    public int compareTo(PrioritizableNode other) {
        // First always pick nodes without violation above nodes with violations
        if (!this.violatesSpares && other.violatesSpares) return -1;
        if (!other.violatesSpares && this.violatesSpares) return 1;
        if (!this.violatesHeadroom && other.violatesHeadroom) return -1;
        if (!other.violatesHeadroom && this.violatesHeadroom) return 1;

        // Choose active nodes
        if (this.node.state().equals(Node.State.active) && !other.node.state().equals(Node.State.active)) return -1;
        if (other.node.state().equals(Node.State.active) && !this.node.state().equals(Node.State.active)) return 1;

        // Choose active node that is not retired first (surplus is active but retired)
        if (!this.isSurplusNode && other.isSurplusNode) return -1;
        if (!other.isSurplusNode && this.isSurplusNode) return 1;

        // Choose inactive nodes
        if (this.node.state().equals(Node.State.inactive) && !other.node.state().equals(Node.State.inactive)) return -1;
        if (other.node.state().equals(Node.State.inactive) && !this.node.state().equals(Node.State.inactive)) return 1;

        // Choose reserved nodes from a previous allocation attempt (the exist in node repo)
        if (isInNodeRepoAndReserved(this) && !isInNodeRepoAndReserved(other)) return -1;
        if (isInNodeRepoAndReserved(other) && !isInNodeRepoAndReserved(this)) return 1;

        // Choose ready nodes
        if (this.node.state().equals(Node.State.ready) && !other.node.state().equals(Node.State.ready)) return -1;
        if (other.node.state().equals(Node.State.ready) && !this.node.state().equals(Node.State.ready)) return 1;

        // The node state should be equal here
        if (!this.node.state().equals(other.node.state())) {
            throw new RuntimeException(
                    String.format("Error during node priority comparison. Node states are not equal as expected. Got %s and %s.",
                                  this.node.state(), other.node.state()));
        }

        // Choose exact flavor
        if (this.preferredOnFlavor && !other.preferredOnFlavor) return -1;
        if (other.preferredOnFlavor && !this.preferredOnFlavor) return 1;

        // Choose docker node over non-docker node (is this to differentiate between docker replaces non-docker flavors?)
        if (this.parent.isPresent() && !other.parent.isPresent()) return -1;
        if (other.parent.isPresent() && !this.parent.isPresent()) return 1;

        // Choose the node with parent node with the least capacity (TODO parameterize this as this is pretty much the core of the algorithm)
        int freeCapacity = this.freeParentCapacity.compare(other.freeParentCapacity);
        if (freeCapacity != 0) return freeCapacity;

        // Choose cheapest node
        if (this.node.flavor().cost() < other.node.flavor().cost()) return -1;
        if (other.node.flavor().cost() < this.node.flavor().cost()) return 1;

        // All else equal choose hostname alphabetically
        return this.node.hostname().compareTo(other.node.hostname());
    }

    private static boolean isInNodeRepoAndReserved(PrioritizableNode nodePri) {
        if (nodePri.isNewNode) return false;
        return nodePri.node.state().equals(Node.State.reserved);
    }

}

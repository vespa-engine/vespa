// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Objects;

/**
 * A specification of a set of nodes.
 * This reflects that nodes can be requested either by count and flavor or by type,
 * and encapsulates the differences in logic between these two cases.
 *
 * @author bratseth
 */
public interface NodeSpec {

    /** The node type this requests */
    NodeType type();

    /** Returns whether the given flavor is compatible with this spec */
    boolean isCompatible(Flavor flavor);

    /** Returns whether the given flavor is exactly specified by this node spec */
    boolean matchesExactly(Flavor flavor);

    /** Returns whether this requests a non-stock flavor */
    boolean specifiesNonStockFlavor();

    /** Returns whether the given node count is sufficient to consider this spec fulfilled to the maximum amount */
    boolean saturatedBy(int count);

    /** Returns whether the given node count is sufficient to fulfill this spec */
    boolean fulfilledBy(int count);

    /** Returns the ideal number of nodes that should be retired to fulfill this spec */
    int idealRetiredCount(int acceptedCount, int currentRetiredCount);

    /** Returns a specification of a fraction of all the nodes of this. It is assumed the argument is a valid divisor. */
    NodeSpec fraction(int divisor);

    /**
     * Assigns the flavor requested by this to the given node and returns it,
     * if one is requested and it is allowed to change.
     * Otherwise, the node is returned unchanged.
     */
    Node assignRequestedFlavor(Node node);

    static NodeSpec from(int nodeCount, Flavor flavor) {
        return new CountNodeSpec(nodeCount, flavor);
    }

    static NodeSpec from(NodeType type) {
        return new TypeNodeSpec(type);
    }

    /** A node spec specifying a node count and a flavor */
    class CountNodeSpec implements NodeSpec {

        private final int count;
        private final Flavor requestedFlavor;

        public CountNodeSpec(int count, Flavor flavor) {
            Objects.requireNonNull(flavor, "A flavor must be specified");
            this.count = count;
            this.requestedFlavor = flavor;
        }

        // TODO: Remove usage of this
        public Flavor getFlavor() {
            return requestedFlavor;
        }

        // TODO: Remove usage of this
        public int getCount()  { return count; }

        @Override
        public NodeType type() { return NodeType.tenant; }

        @Override
        public boolean isCompatible(Flavor flavor) {
            if (flavor.satisfies(requestedFlavor)) return true;
            return requestedFlavorCanBeAchievedByResizing(flavor);
        }

        @Override
        public boolean matchesExactly(Flavor flavor) { return flavor.equals(this.requestedFlavor); }

        @Override
        public boolean specifiesNonStockFlavor() { return ! requestedFlavor.isStock(); }

        @Override
        public boolean fulfilledBy(int count) { return count >= this.count; }

        @Override
        public boolean saturatedBy(int count) { return fulfilledBy(count); } // min=max for count specs

        @Override
        public int idealRetiredCount(int acceptedCount, int currentRetiredCount) { return acceptedCount - this.count; }

        @Override
        public NodeSpec fraction(int divisor) { return new CountNodeSpec(count/divisor, requestedFlavor); }

        @Override
        public Node assignRequestedFlavor(Node node) {
            // Docker nodes can change flavor in place
            if (requestedFlavorCanBeAchievedByResizing(node.flavor()))
                return node.with(requestedFlavor);

            return node;
        }

        @Override
        public String toString() { return "request for " + count + " nodes of " + requestedFlavor; }

        /** Docker nodes can be downsized in place */
        private boolean requestedFlavorCanBeAchievedByResizing(Flavor flavor) {
            // TODO: Enable this when we can do it safely
            // Then also re-enable ProvisioningTest.application_deployment_with_inplace_downsize()
            // return flavor.isDocker() && requestedFlavor.isDocker() && flavor.isLargerThan(requestedFlavor);
            return false;
        }

    }

    /** A node spec specifying a node type. This will accept all nodes of this type. */
    class TypeNodeSpec implements NodeSpec {

        private final NodeType type;

        public TypeNodeSpec(NodeType type) {
            this.type = type;
        }

        @Override
        public NodeType type() { return type; }

        @Override
        public boolean isCompatible(Flavor flavor) { return true; }

        @Override
        public boolean matchesExactly(Flavor flavor) { return false; }

        @Override
        public boolean specifiesNonStockFlavor() { return false; }

        @Override
        public boolean fulfilledBy(int count) { return true; }

        @Override
        public boolean saturatedBy(int count) { return false; }

        @Override
        public int idealRetiredCount(int acceptedCount, int currentRetiredCount) {
            /*
             * All nodes marked with wantToRetire get marked as retired just before this function is called,
             * the job of this function is to throttle the retired count. If no nodes are marked as retired
             * then continue this way, otherwise allow only 1 node to be retired
             */
            return Math.min(1, currentRetiredCount);
        }

        @Override
        public NodeSpec fraction(int divisor) { return this; }

        @Override
        public Node assignRequestedFlavor(Node node) { return node; }

        @Override
        public String toString() { return "request for all nodes of type '" + type + "'"; }

    }

}

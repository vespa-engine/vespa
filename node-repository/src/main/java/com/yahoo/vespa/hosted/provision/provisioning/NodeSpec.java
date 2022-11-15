// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    /** Returns whether the hosts running the nodes of this application can also run nodes of other applications. */
    boolean isExclusive();

    /** Returns whether the given flavor is compatible with this spec */
    boolean isCompatible(Flavor flavor, NodeFlavors flavors);

    /** Returns whether the given node count is sufficient to consider this spec fulfilled to the maximum amount */
    boolean saturatedBy(int count);

    /** Returns whether the given node count is sufficient to fulfill this spec */
    default boolean fulfilledBy(int count) {
        return fulfilledDeficitCount(count) == 0;
    }

    /** Returns whether this should throw an exception if the requested nodes are not fully available */
    boolean canFail();

    /** Returns whether we should retire nodes at all when fulfilling this spec */
    boolean considerRetiring();

    /** Returns the ideal number of nodes that should be retired to fulfill this spec */
    int idealRetiredCount(int acceptedCount, int wantToRetireCount, int currentRetiredCount);

    /** Returns number of additional nodes needed for this spec to be fulfilled given the current node count */
    int fulfilledDeficitCount(int count);

    /** Returns a specification of a fraction of all the nodes of this. It is assumed the argument is a valid divisor. */
    NodeSpec fraction(int divisor);

    /** Returns the resources requested by this or empty if none are explicitly requested */
    Optional<NodeResources> resources();

    /** Returns whether the given node must be resized to match this spec */
    boolean needsResize(Node node);

    /** Returns true if there exist some circumstance where we may accept to have this node allocated */
    boolean acceptable(NodeCandidate node);

    /** Returns true if nodes with non-active parent hosts should be rejected */
    boolean rejectNonActiveParent();

    /** Returns the cloud account to use when fulfilling this spec */
    CloudAccount cloudAccount();

    /**
     * Returns true if a node with given current resources and current spare host resources can be resized
     * in-place to resources in this spec.
     */
    default boolean canResize(NodeResources currentNodeResources, NodeResources currentSpareHostResources,
                              ClusterSpec.Type type, boolean hasTopologyChange, int currentClusterSize) {
        return false;
    }

    static NodeSpec from(int nodeCount, NodeResources resources, boolean exclusive, boolean canFail, CloudAccount cloudAccount) {
        return new CountNodeSpec(nodeCount, resources, exclusive, canFail, cloudAccount);
    }

    static NodeSpec from(NodeType type, CloudAccount cloudAccount) {
        return new TypeNodeSpec(type, cloudAccount);
    }

    /** A node spec specifying a node count and a flavor */
    class CountNodeSpec implements NodeSpec {

        private final int count;
        private final NodeResources requestedNodeResources;
        private final boolean exclusive;
        private final boolean canFail;
        private final CloudAccount cloudAccount;

        private CountNodeSpec(int count, NodeResources resources, boolean exclusive, boolean canFail, CloudAccount cloudAccount) {
            this.count = count;
            this.requestedNodeResources = Objects.requireNonNull(resources, "Resources must be specified");
            this.exclusive = exclusive;
            this.canFail = canFail;
            this.cloudAccount = Objects.requireNonNull(cloudAccount);
        }

        @Override
        public Optional<NodeResources> resources() {
            return Optional.of(requestedNodeResources);
        }

        @Override
        public boolean isExclusive() { return exclusive; }

        @Override
        public NodeType type() { return NodeType.tenant; }

        @Override
        public boolean isCompatible(Flavor flavor, NodeFlavors flavors) {
            if (flavor.isDocker()) { // Docker nodes can satisfy a request for parts of their resources
                return flavor.resources().compatibleWith(requestedNodeResources);
            } else { // Other nodes must be matched exactly
                return requestedNodeResources.equals(flavor.resources());
            }
        }

        @Override
        public boolean saturatedBy(int count) { return fulfilledBy(count); } // min=max for count specs

        @Override
        public boolean canFail() { return canFail; }

        @Override
        public boolean considerRetiring() {
            // If we cannot fail we cannot retire as we may end up without sufficient replacement capacity
            return canFail();
        }

        @Override
        public int idealRetiredCount(int acceptedCount, int wantToRetireCount, int currentRetiredCount) {
            return acceptedCount - this.count - currentRetiredCount;
        }

        @Override
        public int fulfilledDeficitCount(int count) {
            return Math.max(this.count - count, 0);
        }

        @Override
        public NodeSpec fraction(int divisor) {
            return new CountNodeSpec(count/divisor, requestedNodeResources, exclusive, canFail, cloudAccount);
        }

        @Override
        public boolean needsResize(Node node) {
            return ! node.resources().compatibleWith(requestedNodeResources);
        }

        @Override
        public boolean canResize(NodeResources currentNodeResources, NodeResources currentSpareHostResources,
                                 ClusterSpec.Type type, boolean hasTopologyChange, int currentClusterSize) {
            // Never allow in-place resize when also changing topology or decreasing cluster size
            if (hasTopologyChange || count < currentClusterSize) return false;

            // Do not allow increasing cluster size and decreasing node resources at the same time for content nodes
            if (type.isContent() && count > currentClusterSize && !requestedNodeResources.satisfies(currentNodeResources.justNumbers()))
                return false;

            // Otherwise, allowed as long as the host can satisfy the new requested resources
            return currentSpareHostResources.add(currentNodeResources.justNumbers()).satisfies(requestedNodeResources);
        }

        @Override
        public boolean acceptable(NodeCandidate node) { return true; }

        @Override
        public boolean rejectNonActiveParent() {
            return false;
        }

        @Override
        public CloudAccount cloudAccount() {
            return cloudAccount;
        }

        @Override
        public String toString() { return "request for " + count + " nodes with " + requestedNodeResources; }

    }

    /** A node spec specifying a node type. This will accept all nodes of this type. */
    class TypeNodeSpec implements NodeSpec {

        private static final Map<NodeType, Integer> WANTED_NODE_COUNT = Map.of(NodeType.config, 3,
                                                                               NodeType.controller, 3);

        private final NodeType type;
        private final CloudAccount cloudAccount;

        public TypeNodeSpec(NodeType type, CloudAccount cloudAccount) {
            this.type = type;
            this.cloudAccount = cloudAccount;
        }

        @Override
        public NodeType type() { return type; }

        @Override
        public boolean isExclusive() { return false; }

        @Override
        public boolean isCompatible(Flavor flavor, NodeFlavors flavors) { return true; }

        @Override
        public boolean saturatedBy(int count) { return false; }

        @Override
        public boolean canFail() { return false; }

        @Override
        public boolean considerRetiring() { return true; }

        @Override
        public int idealRetiredCount(int acceptedCount, int wantToRetireCount, int currentRetiredCount) {
            return wantToRetireCount - currentRetiredCount;
        }

        @Override
        public int fulfilledDeficitCount(int count) {
            // If no wanted count is specified for this node type, then any count fulfills the deficit
            return Math.max(0, WANTED_NODE_COUNT.getOrDefault(type, 0) - count);
        }

        @Override
        public NodeSpec fraction(int divisor) { return this; }

        @Override
        public Optional<NodeResources> resources() {
            return Optional.empty();
        }

        @Override
        public boolean needsResize(Node node) { return false; }

        @Override
        public boolean acceptable(NodeCandidate node) {
            // Since we consume all offered nodes we should not accept previously deactivated nodes
            return node.state() != Node.State.inactive;
        }

        @Override
        public boolean rejectNonActiveParent() {
            return true;
        }

        @Override
        public CloudAccount cloudAccount() {
            return cloudAccount;
        }

        @Override
        public String toString() { return "request for all nodes of type '" + type + "'"; }

    }

}

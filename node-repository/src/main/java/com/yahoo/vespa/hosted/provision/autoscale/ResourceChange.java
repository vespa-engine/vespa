// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;

import java.time.Duration;

/**
 * A resource change.
 *
 * @author bratseth
 */
public class ResourceChange {

    private final AllocatableResources from, to;
    private final ClusterModel model;

    public ResourceChange(ClusterModel model, AllocatableResources to) {
        this.from = model.current();
        this.to = to;
        this.model = model;
    }

    /** Returns the estimated total cost of this resource change (coming in addition to the "to" resource cost). */
    public double cost() {
        if (model.isContent()) {
            if (requiresNodeReplacement()) return toHours(model.redistributionDuration()) * from.cost();
            return toHours(model.redistributionDuration()) * from.advertisedResources().cost() * nodesToRetire();
        }
        else {
            if (requiresNodeReplacement()) return toHours(model.nodeReplacementDuration()) * from.cost();
            return 0;
        }
    }

    private boolean requiresRedistribution() {
        if ( ! model.clusterSpec().type().isContent()) return false;
        if (from.nodes() != to.nodes()) return true;
        if (from.groups() != to.groups()) return true;
        if (requiresNodeReplacement()) return true;
        return false;
    }

    /**
     * Returns the estimated number of nodes that will be retired by this change,
     * given that it is a content cluster and no node replacement is necessary.
     * This is not necessarily always perfectly correct if this changes group layout.
     */
    private int nodesToRetire() {
        return Math.max(0, from.nodes() - to.nodes());
    }

    /** Returns true if the *existing* nodes of this needs to be replaced in this change. */
    private boolean requiresNodeReplacement() {
        var fromNodes = from.advertisedResources().nodeResources();
        var toNodes = to.advertisedResources().nodeResources();

        if (model.isExclusive()) {
            return ! fromNodes.equals(toNodes);
        }
        else {
            if ( ! fromNodes.justNonNumbers().equalsWhereSpecified(toNodes.justNonNumbers())) return true;
            if ( ! canInPlaceResize()) return true;
            return false;
        }
    }

    private double toHours(Duration duration) {
        return duration.toMillis() / 3600000.0;
    }

    private boolean canInPlaceResize() {
        return canInPlaceResize(from.nodes(), from.advertisedResources().nodeResources(),
                                to.nodes(), to.advertisedResources().nodeResources(),
                                model.clusterSpec().type(), model.isExclusive(), from.groups() != to.groups());
    }

    public static boolean canInPlaceResize(int fromCount, NodeResources fromResources,
                                           int toCount, NodeResources toResources,
                                           ClusterSpec.Type type, boolean exclusive, boolean hasTopologyChange) {
        if (exclusive) return false; // exclusive resources must match the host

        // Never allow in-place resize when also changing topology or decreasing cluster size
        if (hasTopologyChange || toCount < fromCount) return false;

        // Do not allow increasing cluster size and decreasing node resources at the same time for content nodes
        if (type.isContent() && toCount > fromCount && !toResources.satisfies(fromResources.justNumbers()))
            return false;

        return true;
    }

}

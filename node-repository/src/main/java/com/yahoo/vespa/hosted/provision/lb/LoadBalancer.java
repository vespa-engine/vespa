// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.vespa.hosted.provision.maintenance.LoadBalancerExpirer;

import java.util.Objects;

/**
 * Represents a load balancer for an application's cluster. This is immutable.
 *
 * @author mpolden
 */
public class LoadBalancer {

    private final LoadBalancerId id;
    private final LoadBalancerInstance instance;
    private final boolean inactive;

    public LoadBalancer(LoadBalancerId id, LoadBalancerInstance instance, boolean inactive) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.instance = Objects.requireNonNull(instance, "instance must be non-null");
        this.inactive = inactive;
    }

    /** An identifier for this load balancer. The ID is unique inside the zone */
    public LoadBalancerId id() {
        return id;
    }

    /** The instance associated with this */
    public LoadBalancerInstance instance() {
        return instance;
    }

    /**
     * Returns whether this load balancer is inactive. Inactive load balancers are eventually removed by
     * {@link LoadBalancerExpirer}. Inactive load balancers may be reactivated if a deleted cluster is redeployed.
     */
    public boolean inactive() {
        return inactive;
    }

    /** Return a copy of this that is set inactive */
    public LoadBalancer deactivate() {
        return new LoadBalancer(id, instance, true);
    }

}

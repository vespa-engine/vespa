// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.collections.AbstractFilteringList;

import java.util.Collection;

/**
 * A filterable load balancer list. This is immutable.
 *
 * @author mpolden
 */
public class LoadBalancerList extends AbstractFilteringList<LoadBalancer, LoadBalancerList> {

    protected LoadBalancerList(Collection<LoadBalancer> items, boolean negate) {
        super(items, negate, LoadBalancerList::new);
    }

    /** Returns the subset of load balancers that are in given state */
    public LoadBalancerList in(LoadBalancer.State state) {
        return matching(lb -> lb.state() == state);
    }

    public static LoadBalancerList copyOf(Collection<LoadBalancer> loadBalancers) {
        return new LoadBalancerList(loadBalancers, false);
    }

}

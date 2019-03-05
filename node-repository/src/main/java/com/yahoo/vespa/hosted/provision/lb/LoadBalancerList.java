// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A filterable load balancer list.
 *
 * @author mpolden
 */
public class LoadBalancerList {

    private final List<LoadBalancer> loadBalancers;

    public LoadBalancerList(Collection<LoadBalancer> loadBalancers) {
        this.loadBalancers = List.copyOf(Objects.requireNonNull(loadBalancers, "loadBalancers must be non-null"));
    }

    /** Returns the subset of load balancers owned by given application */
    public LoadBalancerList owner(ApplicationId application) {
        return of(loadBalancers.stream().filter(lb -> lb.id().application().equals(application)));
    }

    /** Returns the subset of load balancers that are inactive */
    public LoadBalancerList inactive() {
        return of(loadBalancers.stream().filter(LoadBalancer::inactive));
    }

    public List<LoadBalancer> asList() {
        return loadBalancers;
    }

    private static LoadBalancerList of(Stream<LoadBalancer> stream) {
        return new LoadBalancerList(stream.collect(Collectors.toUnmodifiableList()));
    }

}

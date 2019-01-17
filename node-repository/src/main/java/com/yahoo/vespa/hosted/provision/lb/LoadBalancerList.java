// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.ApplicationId;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;

/**
 * A filterable load balancer list
 *
 * @author mpolden
 */
public class LoadBalancerList {

    private final List<LoadBalancer> loadBalancers;

    public LoadBalancerList(Collection<LoadBalancer> loadBalancers) {
        this.loadBalancers = ImmutableList.copyOf(Objects.requireNonNull(loadBalancers, "loadBalancers must be non-null"));
    }

    /** Returns the subset of load balancers owned by given application */
    public LoadBalancerList owner(ApplicationId application) {
        return loadBalancers.stream()
                            .filter(lb -> lb.id().application().equals(application))
                            .collect(collectingAndThen(Collectors.toList(), LoadBalancerList::new));
    }

    public List<LoadBalancer> asList() {
        return loadBalancers;
    }

}

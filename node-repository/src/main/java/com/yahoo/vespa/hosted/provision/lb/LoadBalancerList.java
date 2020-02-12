// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A filterable load balancer list. This is immutable.
 *
 * @author mpolden
 */
public class LoadBalancerList implements Iterable<LoadBalancer> {

    public static LoadBalancerList EMPTY = new LoadBalancerList(List.of());

    private final List<LoadBalancer> loadBalancers;

    private LoadBalancerList(Collection<LoadBalancer> loadBalancers) {
        this.loadBalancers = List.copyOf(Objects.requireNonNull(loadBalancers, "loadBalancers must be non-null"));
    }

    /** Returns the subset of load balancers that are in given state */
    public LoadBalancerList in(LoadBalancer.State state) {
        return of(loadBalancers.stream().filter(lb -> lb.state() == state));
    }

    /** Returns the subset of load balancers that last changed before given instant */
    public LoadBalancerList changedBefore(Instant instant) {
        return of(loadBalancers.stream().filter(lb -> lb.changedAt().isBefore(instant)));
    }

    public List<LoadBalancer> asList() {
        return loadBalancers;
    }

    private static LoadBalancerList of(Stream<LoadBalancer> stream) {
        return new LoadBalancerList(stream.collect(Collectors.toUnmodifiableList()));
    }

    public static LoadBalancerList copyOf(Collection<LoadBalancer> loadBalancers) {
        return new LoadBalancerList(loadBalancers);
    }

    @Override
    public Iterator<LoadBalancer> iterator() {
        return loadBalancers.iterator();
    }

}

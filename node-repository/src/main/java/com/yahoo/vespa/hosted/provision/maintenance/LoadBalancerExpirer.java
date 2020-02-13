// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer.State;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Periodically expire load balancers.
 *
 * Load balancers expire from the following states:
 *
 * {@link LoadBalancer.State#inactive}: An application is removed and load balancers are deactivated.
 * {@link LoadBalancer.State#reserved}: An prepared application is never successfully activated, thus never activating
 *                                      any prepared load balancers.
 *
 * @author mpolden
 */
public class LoadBalancerExpirer extends Maintainer {

    private static final Duration reservedExpiry = Duration.ofHours(1);
    private static final Duration inactiveExpiry = Duration.ofHours(1);

    private final LoadBalancerService service;
    private final CuratorDatabaseClient db;

    LoadBalancerExpirer(NodeRepository nodeRepository, Duration interval, LoadBalancerService service) {
        super(nodeRepository, interval);
        this.service = Objects.requireNonNull(service, "service must be non-null");
        this.db = nodeRepository.database();
    }

    @Override
    protected void maintain() {
        expireReserved();
        removeInactive();
        pruneReals();
    }

    /** Move reserved load balancer that have expired to inactive */
    private void expireReserved() {
        var now = nodeRepository().clock().instant();
        withLoadBalancersIn(State.reserved, lb -> {
            var gracePeriod = now.minus(reservedExpiry);
            if (!lb.changedAt().isBefore(gracePeriod)) return; // Should not move to inactive yet
            db.writeLoadBalancer(lb.with(State.inactive, now));
        });
    }

    /** Deprovision inactive load balancers that have expired */
    private void removeInactive() {
        var failed = new ArrayList<LoadBalancerId>();
        var lastException = new AtomicReference<Exception>();
        var now = nodeRepository().clock().instant();
        withLoadBalancersIn(State.inactive, lb -> {
            var gracePeriod = now.minus(inactiveExpiry);
            if (!lb.changedAt().isBefore(gracePeriod)) return; // Should not be removed yet
            if (!allocatedNodes(lb.id()).isEmpty()) return;    // Still has nodes, do not remove
            try {
                service.remove(lb.id().application(), lb.id().cluster());
                db.removeLoadBalancer(lb.id());
            } catch (Exception e){
                failed.add(lb.id());
                lastException.set(e);
            }
        });
        if (!failed.isEmpty()) {
            log.log(LogLevel.WARNING, String.format("Failed to remove %d load balancers: %s, retrying in %s",
                                                    failed.size(),
                                                    failed.stream()
                                                          .map(LoadBalancerId::serializedForm)
                                                          .collect(Collectors.joining(", ")),
                                                    interval()),
                    lastException.get());
        }
    }

    /** Remove reals from inactive load balancers */
    private void pruneReals() {
        var failed = new ArrayList<LoadBalancerId>();
        var lastException = new AtomicReference<Exception>();
        withLoadBalancersIn(State.inactive, lb -> {
            var allocatedNodes = allocatedNodes(lb.id()).stream().map(Node::hostname).collect(Collectors.toSet());
            var reals = new LinkedHashSet<>(lb.instance().reals());
            // Remove any real no longer allocated to this application
            reals.removeIf(real -> !allocatedNodes.contains(real.hostname().value()));
            try {
                service.create(lb.id().application(), lb.id().cluster(), reals, true);
                db.writeLoadBalancer(lb.with(lb.instance().withReals(reals)));
            } catch (Exception e) {
                failed.add(lb.id());
                lastException.set(e);
            }
        });
        if (!failed.isEmpty()) {
            log.log(LogLevel.WARNING, String.format("Failed to remove reals from %d load balancers: %s, retrying in %s",
                                                    failed.size(),
                                                    failed.stream()
                                                          .map(LoadBalancerId::serializedForm)
                                                          .collect(Collectors.joining(", ")),
                                                    interval()),
                    lastException.get());
        }
    }

    /** Apply operation to all load balancers that exist in given state, while holding lock */
    private void withLoadBalancersIn(LoadBalancer.State state, Consumer<LoadBalancer> operation) {
        try (var legacyLock = db.lockLoadBalancers()) {
            for (var id : db.readLoadBalancerIds()) {
                try (var lock = db.lockLoadBalancers(id.application())) {
                    var loadBalancer = db.readLoadBalancer(id);
                    if (loadBalancer.isEmpty()) continue;              // Load balancer was removed during loop
                    if (loadBalancer.get().state() != state) continue; // Wrong state
                    operation.accept(loadBalancer.get());
                }
            }
        }
    }

    private List<Node> allocatedNodes(LoadBalancerId loadBalancer) {
        return nodeRepository().list().owner(loadBalancer.application()).cluster(loadBalancer.cluster()).asList();
    }

}

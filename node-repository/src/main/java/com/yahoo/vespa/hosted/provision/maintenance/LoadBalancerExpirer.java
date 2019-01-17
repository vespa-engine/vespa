// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Periodically remove inactive load balancers permanently.
 *
 * When an application is removed, any associated load balancers are only deactivated. This maintainer ensures that
 * such resources are eventually freed.
 *
 * @author mpolden
 */
public class LoadBalancerExpirer extends Maintainer {

    private final LoadBalancerService service;
    private final CuratorDatabaseClient db;

    public LoadBalancerExpirer(NodeRepository nodeRepository, Duration interval, JobControl jobControl,
                               LoadBalancerService service) {
        super(nodeRepository, interval, jobControl);
        this.service = Objects.requireNonNull(service, "service must be non-null");
        this.db = nodeRepository.database();
    }

    @Override
    protected void maintain() {
        removeInactive();
    }

    private void removeInactive() {
        List<LoadBalancerId> failed = new ArrayList<>();
        Exception lastException = null;
        try (Lock lock = db.lockLoadBalancers()) {
            for (LoadBalancer loadBalancer : nodeRepository().loadBalancers().inactive().asList()) {
                if (hasNodes(loadBalancer.id().application())) { // Defer removal if there are still nodes allocated to application
                    continue;
                }
                try {
                    service.remove(loadBalancer.id());
                    db.removeLoadBalancer(loadBalancer.id());
                } catch (Exception e) {
                    failed.add(loadBalancer.id());
                    lastException = e;
                }
            }
        }
        if (!failed.isEmpty()) {
            log.log(LogLevel.WARNING, String.format("Failed to remove %d load balancers: %s, retrying in %s",
                                                    failed.size(),
                                                    failed.stream()
                                                          .map(LoadBalancerId::serializedForm)
                                                          .collect(Collectors.joining(", ")),
                                                    interval()),
                    lastException);
        }
    }

    private boolean hasNodes(ApplicationId application) {
        return !nodeRepository().getNodes(application).isEmpty();
    }

}

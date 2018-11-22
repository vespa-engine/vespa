// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.net.InetAddresses;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides provisioning of load balancers for applications.
 *
 * @author mpolden
 */
public class LoadBalancerProvisioner {

    private final NodeRepository nodeRepository;
    private final CuratorDatabaseClient db;
    private final LoadBalancerService service;

    public LoadBalancerProvisioner(NodeRepository nodeRepository, LoadBalancerService service) {
        this.nodeRepository = nodeRepository;
        this.db = nodeRepository.database();
        this.service = service;
    }

    /**
     * Provision load balancer(s) for given application.
     *
     * If the application has multiple container clusters, one load balancer will be provisioned for each cluster.
     */
    public Map<LoadBalancerId, LoadBalancer> provision(ApplicationId application) {
        try (Mutex applicationLock = nodeRepository.lock(application)) {
            try (Mutex loadBalancersLock = db.lockLoadBalancers()) {
                Map<LoadBalancerId, LoadBalancer> loadBalancers = new LinkedHashMap<>();
                for (Map.Entry<ClusterSpec.Id, List<Node>> kv : activeContainers(application).entrySet()) {
                    LoadBalancer loadBalancer = create(application, kv.getKey(), kv.getValue());
                    loadBalancers.put(loadBalancer.id(), loadBalancer);
                    db.writeLoadBalancer(loadBalancer);
                }
                return Collections.unmodifiableMap(loadBalancers);
            }
        }
    }

    /** Mark all load balancers assigned to given application as deleted */
    public void remove(ApplicationId application) {
        try (Mutex applicationLock = nodeRepository.lock(application)) {
            try (Mutex loadBalancersLock = db.lockLoadBalancers()) {
                if (!activeContainers(application).isEmpty()) {
                    throw new IllegalArgumentException(application + " has active containers, refusing to remove load balancers");
                }
                db.readLoadBalancers(application)
                  .stream()
                  .map(LoadBalancer::delete)
                  .forEach(db::writeLoadBalancer);
            }
        }
    }

    private LoadBalancer create(ApplicationId application, ClusterSpec.Id cluster, List<Node> nodes) {
        Map<HostName, Set<String>> hostnameToIpAdresses = nodes.stream()
                                                               .collect(Collectors.toMap(node -> HostName.from(node.hostname()),
                                                                                         this::reachableIpAddresses));
        List<Real> reals = new ArrayList<>();
        hostnameToIpAdresses.forEach((hostname, ipAddresses) -> {
            ipAddresses.forEach(ipAddress -> reals.add(new Real(hostname, ipAddress)));
        });
        return service.create(application, cluster, reals);
    }

    /** Returns a list of active containers for given application, grouped by cluster ID */
    private Map<ClusterSpec.Id, List<Node>> activeContainers(ApplicationId application) {
        return new NodeList(nodeRepository.getNodes())
                .owner(application)
                .in(Node.State.active)
                .type(ClusterSpec.Type.container)
                .asList()
                .stream()
                .collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster().id()));
    }

    /** Find IP addresses reachable by the load balancer service */
    private Set<String> reachableIpAddresses(Node node) {
        Set<String> reachable = new LinkedHashSet<>(node.ipAddresses());
        // Remove addresses unreachable by the load balancer service
        switch (service.protocol()) {
            case ipv4:
                reachable.removeIf(this::isIpv6);
                break;
            case ipv6:
                reachable.removeIf(this::isIpv4);
                break;
        }
        return reachable;
    }

    private boolean isIpv4(String ipAddress) {
        return InetAddresses.forString(ipAddress) instanceof Inet4Address;
    }

    private boolean isIpv6(String ipAddress) {
        return InetAddresses.forString(ipAddress) instanceof Inet6Address;
    }

}

// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

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
                for (Map.Entry<ClusterSpec, List<Node>> kv : activeContainers(application).entrySet()) {
                    LoadBalancerId id = new LoadBalancerId(application, kv.getKey().id());
                    LoadBalancerInstance instance = create(application, kv.getKey().id(), kv.getValue());
                    // Load balancer is always re-activated here to avoid reallocation if an application/cluster is
                    // deleted and then redeployed.
                    LoadBalancer loadBalancer = new LoadBalancer(id, instance, kv.getKey().rotations(), false);
                    loadBalancers.put(loadBalancer.id(), loadBalancer);
                    db.writeLoadBalancer(loadBalancer);
                }
                return Collections.unmodifiableMap(loadBalancers);
            }
        }
    }

    /**
     * Deactivate all load balancers assigned to given application. This is a no-op if an application does not have any
     * load balancer(s)
     */
    public void deactivate(ApplicationId application, NestedTransaction transaction) {
        try (Mutex applicationLock = nodeRepository.lock(application)) {
            try (Mutex loadBalancersLock = db.lockLoadBalancers()) {
                List<LoadBalancer> deactivatedLoadBalancers = nodeRepository.loadBalancers().owner(application).asList().stream()
                                                                            .map(LoadBalancer::deactivate)
                                                                            .collect(Collectors.toList());
                db.writeLoadBalancers(deactivatedLoadBalancers, transaction);
            }
        }
    }

    private LoadBalancerInstance create(ApplicationId application, ClusterSpec.Id cluster, List<Node> nodes) {
        Map<HostName, Set<String>> hostnameToIpAdresses = nodes.stream()
                                                               .collect(Collectors.toMap(node -> HostName.from(node.hostname()),
                                                                                         this::reachableIpAddresses));
        Set<Real> reals = new LinkedHashSet<>();
        hostnameToIpAdresses.forEach((hostname, ipAddresses) -> {
            ipAddresses.forEach(ipAddress -> reals.add(new Real(hostname, ipAddress)));
        });
        return service.create(application, cluster, reals);
    }

    /** Returns a list of active containers for given application, grouped by cluster spec */
    private Map<ClusterSpec, List<Node>> activeContainers(ApplicationId application) {
        return new NodeList(nodeRepository.getNodes(NodeType.tenant, Node.State.active))
                .owner(application)
                .filter(node -> node.state().isAllocated())
                .type(ClusterSpec.Type.container)
                .asList()
                .stream()
                .collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster()));
    }

    /** Find IP addresses reachable by the load balancer service */
    private Set<String> reachableIpAddresses(Node node) {
        Set<String> reachable = new LinkedHashSet<>(node.ipAddresses());
        // Remove addresses unreachable by the load balancer service
        switch (service.protocol()) {
            case ipv4:
                reachable.removeIf(IP::isV6);
                break;
            case ipv6:
                reachable.removeIf(IP::isV4);
                break;
        }
        return reachable;
    }

}

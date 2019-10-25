// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.AliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.RoutingId;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Updates routing policies and their associated DNS records based on an deployment's load balancers.
 *
 * @author mortent
 * @author mpolden
 */
public class RoutingPolicies {

    private final Controller controller;
    private final CuratorDb db;

    public RoutingPolicies(Controller controller) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.db = controller.curator();
        try (var lock = db.lockRoutingPolicies()) { // Update serialized format
            for (var policy : db.readRoutingPolicies().entrySet()) {
                db.writeRoutingPolicies(policy.getKey(), policy.getValue());
            }
        }
    }

    /** Read all known routing policies for given instance */
    public Set<RoutingPolicy> get(ApplicationId application) {
        return db.readRoutingPolicies(application);
    }

    /** Read all known routing policies for given deployment */
    public Set<RoutingPolicy> get(DeploymentId deployment) {
        return get(deployment.applicationId(), deployment.zoneId());
    }

    /** Read all known routing policies for given deployment */
    public Set<RoutingPolicy> get(ApplicationId application, ZoneId zone) {
        return db.readRoutingPolicies(application).stream()
                 .filter(policy -> policy.zone().equals(zone))
                 .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Refresh routing policies for application in given zone. This is idempotent and changes will only be performed if
     * load balancers for given application have changed.
     */
    public void refresh(ApplicationId application, DeploymentSpec deploymentSpec, ZoneId zone) {
        if (!controller.zoneRegistry().zones().directlyRouted().ids().contains(zone)) return;
        var lbs = new AllocatedLoadBalancers(application, zone, controller.serviceRegistry().configServer().getLoadBalancers(application, zone));
        try (var lock = db.lockRoutingPolicies()) {
            removeObsoleteEndpointsFromDns(lbs, deploymentSpec, lock);
            storePoliciesOf(lbs, deploymentSpec, lock);
            removeObsoletePolicies(lbs, lock);
            registerEndpointsInDns(lbs, lock);
        }
    }

    /** Create global endpoints for given route, if any */
    private void registerEndpointsInDns(AllocatedLoadBalancers loadBalancers, @SuppressWarnings("unused") Lock lock) {
        Map<RoutingId, List<RoutingPolicy>> routingTable = routingTableFrom(get(loadBalancers.application));

        // Create DNS record for each routing ID
        for (Map.Entry<RoutingId, List<RoutingPolicy>> routeEntry : routingTable.entrySet()) {
            Endpoint endpoint = RoutingPolicy.endpointOf(routeEntry.getKey().application(), routeEntry.getKey().endpointId(),
                                                         controller.system());
            Set<AliasTarget> targets = routeEntry.getValue()
                                                 .stream()
                                                 .filter(policy -> policy.dnsZone().isPresent())
                                                 .map(policy -> new AliasTarget(policy.canonicalName(),
                                                                                policy.dnsZone().get(),
                                                                                policy.zone()))
                                                 .collect(Collectors.toSet());
            controller.nameServiceForwarder().createAlias(RecordName.from(endpoint.dnsName()), targets, Priority.normal);
        }
    }

    /** Store routing policies for given route */
    private void storePoliciesOf(AllocatedLoadBalancers loadBalancers, DeploymentSpec spec, @SuppressWarnings("unused") Lock lock) {
        Set<RoutingPolicy> policies = new LinkedHashSet<>(get(loadBalancers.application));
        for (LoadBalancer loadBalancer : loadBalancers.list) {
            spec.instance(loadBalancer.application().instance()).ifPresent(instanceSpec -> {
                RoutingPolicy policy = createPolicy(loadBalancers.application, instanceSpec, loadBalancers.zone, loadBalancer);
                if (!policies.add(policy)) {
                    policies.remove(policy);
                    policies.add(policy);
                }
            });
        }
        db.writeRoutingPolicies(loadBalancers.application, policies);
    }

    /** Create a policy for given load balancer and register a CNAME for it */
    private RoutingPolicy createPolicy(ApplicationId application, DeploymentInstanceSpec instanceSpec, ZoneId zone,
                                       LoadBalancer loadBalancer) {
        var endpoints = endpointIdsOf(loadBalancer, zone, instanceSpec);
        var routingPolicy = new RoutingPolicy(application, loadBalancer.cluster(), zone,
                                              loadBalancer.hostname(), loadBalancer.dnsZone(),
                                              endpoints);
        var name = RecordName.from(routingPolicy.endpointIn(controller.system()).dnsName());
        var data = RecordData.fqdn(loadBalancer.hostname().value());
        controller.nameServiceForwarder().createCname(name, data, Priority.normal);
        return routingPolicy;
    }

    /** Remove obsolete policies for given route and their CNAME records */
    private void removeObsoletePolicies(AllocatedLoadBalancers loadBalancers, @SuppressWarnings("unused") Lock lock) {
        var allPolicies = new LinkedHashSet<>(get(loadBalancers.application));
        var removalCandidates = new HashSet<>(allPolicies);
        var activeLoadBalancers = loadBalancers.list.stream()
                                                    .map(LoadBalancer::hostname)
                                                    .collect(Collectors.toSet());
        // Remove active load balancers and irrelevant zones from candidates
        removalCandidates.removeIf(policy -> activeLoadBalancers.contains(policy.canonicalName()) ||
                                             !policy.zone().equals(loadBalancers.zone));
        for (var policy : removalCandidates) {
            var dnsName = policy.endpointIn(controller.system()).dnsName();
            controller.nameServiceForwarder().removeRecords(Record.Type.CNAME, RecordName.from(dnsName), Priority.normal);
            allPolicies.remove(policy);
        }
        db.writeRoutingPolicies(loadBalancers.application, allPolicies);
    }

    /** Remove unreferenced global endpoints for given route from DNS */
    private void removeObsoleteEndpointsFromDns(AllocatedLoadBalancers loadBalancers, DeploymentSpec deploymentSpec, @SuppressWarnings("unused") Lock lock) {
        var zonePolicies = get(loadBalancers.application, loadBalancers.zone);
        var removalCandidates = routingTableFrom(zonePolicies).keySet();
        var activeRoutingIds = routingIdsFrom(loadBalancers, deploymentSpec);
        removalCandidates.removeAll(activeRoutingIds);
        for (var id : removalCandidates) {
            Endpoint endpoint = RoutingPolicy.endpointOf(id.application(), id.endpointId(), controller.system());
            controller.nameServiceForwarder().removeRecords(Record.Type.ALIAS, RecordName.from(endpoint.dnsName()), Priority.normal);
        }
    }

    /** Compute routing IDs from given load balancers */
    private static Set<RoutingId> routingIdsFrom(AllocatedLoadBalancers loadBalancers, DeploymentSpec spec) {
        Set<RoutingId> routingIds = new LinkedHashSet<>();
        for (var loadBalancer : loadBalancers.list) {
            spec.instance(loadBalancer.application().instance()).ifPresent(instanceSpec -> {
                for (var endpointId : endpointIdsOf(loadBalancer, loadBalancers.zone, instanceSpec)) {
                    routingIds.add(new RoutingId(loadBalancer.application(), endpointId));
                }
            });
        }
        return Collections.unmodifiableSet(routingIds);
    }

    /** Compute a routing table from given policies */
    private static Map<RoutingId, List<RoutingPolicy>> routingTableFrom(Set<RoutingPolicy> routingPolicies) {
        var routingTable = new LinkedHashMap<RoutingId, List<RoutingPolicy>>();
        for (var policy : routingPolicies) {
            for (var rotation : policy.endpoints()) {
                var id = new RoutingId(policy.owner(), rotation);
                routingTable.putIfAbsent(id, new ArrayList<>());
                routingTable.get(id).add(policy);
            }
        }
        return routingTable;
    }

    /** Compute all endpoint IDs of given load balancer */
    private static Set<EndpointId> endpointIdsOf(LoadBalancer loadBalancer, ZoneId zone, DeploymentInstanceSpec spec) {
        return spec.endpoints().stream()
                   .filter(endpoint -> endpoint.containerId().equals(loadBalancer.cluster().value()))
                   .filter(endpoint -> endpoint.regions().contains(zone.region()))
                   .map(com.yahoo.config.application.api.Endpoint::endpointId)
                   .map(EndpointId::of)
                   .collect(Collectors.toSet());
    }

    /** Load balancers allocated to a deployment */
    private static class AllocatedLoadBalancers {

        private final ApplicationId application;
        private final ZoneId zone;
        private final List<LoadBalancer> list;

        private AllocatedLoadBalancers(ApplicationId application, ZoneId zone, List<LoadBalancer> loadBalancers) {
            this.application = application;
            this.zone = zone;
            this.list = loadBalancers.stream()
                                     .filter(AllocatedLoadBalancers::shouldUpdatePolicy)
                                     .collect(Collectors.toUnmodifiableList());
        }

        private static boolean shouldUpdatePolicy(LoadBalancer loadBalancer) {
            switch (loadBalancer.state()) {
                case active:
                case reserved: // This allows DNS updates to happen early, while an application is being prepared.
                    return true;
            }
            // Any other state, such as inactive, is ignored.
            return false;
        }

    }

}

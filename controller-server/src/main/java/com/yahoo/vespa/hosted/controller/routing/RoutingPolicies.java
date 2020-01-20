// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

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
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
    public Map<RoutingPolicyId, RoutingPolicy> get(ApplicationId application) {
        return db.readRoutingPolicies(application);
    }

    /** Read all known routing policies for given deployment */
    public Map<RoutingPolicyId, RoutingPolicy> get(DeploymentId deployment) {
        return get(deployment.applicationId(), deployment.zoneId());
    }

    /** Read all known routing policies for given deployment */
    public Map<RoutingPolicyId, RoutingPolicy> get(ApplicationId application, ZoneId zone) {
        return db.readRoutingPolicies(application).entrySet()
                 .stream()
                 .filter(kv -> kv.getKey().zone().equals(zone))
                 .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Refresh routing policies for application in given zone. This is idempotent and changes will only be performed if
     * load balancers for given application have changed.
     */
    public void refresh(ApplicationId application, DeploymentSpec deploymentSpec, ZoneId zone) {
        if (!controller.zoneRegistry().zones().directlyRouted().ids().contains(zone)) return;
        var loadBalancers = new AllocatedLoadBalancers(application, zone, controller.serviceRegistry().configServer()
                                                                                    .getLoadBalancers(application, zone),
                                                       deploymentSpec);
        var inactiveZones = inactiveZones(application, deploymentSpec);
        try (var lock = db.lockRoutingPolicies()) {
            removeGlobalDnsUnreferencedBy(loadBalancers, lock);
            storePoliciesOf(loadBalancers, lock);
            removePoliciesUnreferencedBy(loadBalancers, lock);
            updateGlobalDnsOf(get(loadBalancers.application).values(), inactiveZones, lock);
        }
    }

    /** Set the status of all global endpoints in given zone */
    public void setGlobalRoutingStatus(ZoneId zone, GlobalRouting.Status status) {
        try (var lock = db.lockRoutingPolicies()) {
            db.writeZoneRoutingPolicy(new ZoneRoutingPolicy(zone, GlobalRouting.status(status, GlobalRouting.Agent.operator,
                                                                                       controller.clock().instant())));
            var allPolicies = db.readRoutingPolicies();
            for (var applicationPolicies : allPolicies.values()) {
                updateGlobalDnsOf(applicationPolicies.values(), Set.of(), lock);
            }
        }
    }

    /** Set the status of all global endpoints for given deployment */
    public void setGlobalRoutingStatus(DeploymentId deployment, GlobalRouting.Status status, GlobalRouting.Agent agent) {
        try (var lock = db.lockRoutingPolicies()) {
            var policies = get(deployment.applicationId());
            var newPolicies = new LinkedHashMap<>(policies);
            for (var policy : policies.values()) {
                if (!policy.id().zone().equals(deployment.zoneId())) continue; // Wrong zone
                var newPolicy = policy.with(policy.status().with(GlobalRouting.status(status, agent,
                                                                                      controller.clock().instant())));
                newPolicies.put(policy.id(), newPolicy);
            }
            db.writeRoutingPolicies(deployment.applicationId(), newPolicies);
            updateGlobalDnsOf(newPolicies.values(), Set.of(), lock);
        }
    }

    /** Update global DNS record for given policies */
    private void updateGlobalDnsOf(Collection<RoutingPolicy> routingPolicies, Set<ZoneId> inactiveZones, @SuppressWarnings("unused") Lock lock) {
        // Create DNS record for each routing ID
        var routingTable = routingTableFrom(routingPolicies);
        for (Map.Entry<RoutingId, List<RoutingPolicy>> routeEntry : routingTable.entrySet()) {
            var targets = new LinkedHashSet<AliasTarget>();
            var staleTargets = new LinkedHashSet<AliasTarget>();
            for (var policy : routeEntry.getValue()) {
                if (policy.dnsZone().isEmpty()) continue;
                var target = new AliasTarget(policy.canonicalName(), policy.dnsZone().get(), policy.id().zone());
                var zonePolicy = db.readZoneRoutingPolicy(policy.id().zone());
                // Remove target zone if global routing status is set out at:
                // - zone level (ZoneRoutingPolicy)
                // - deployment level (RoutingPolicy)
                // - application package level (deployment.xml)
                if (anyOut(zonePolicy.globalRouting(), policy.status().globalRouting()) ||
                    inactiveZones.contains(policy.id().zone())) {
                    staleTargets.add(target);
                } else {
                    targets.add(target);
                }
            }
            if (!targets.isEmpty()) {
                var endpoint = RoutingPolicy.globalEndpointOf(routeEntry.getKey().application(),
                                                              routeEntry.getKey().endpointId(), controller.system());
                controller.nameServiceForwarder().createAlias(RecordName.from(endpoint.dnsName()), targets, Priority.normal);
            }
            staleTargets.forEach(t -> controller.nameServiceForwarder().removeRecords(Record.Type.ALIAS, t.asData(), Priority.normal));
        }
    }

    /** Store routing policies for given load balancers. Returns the persisted policies. */
    private void storePoliciesOf(AllocatedLoadBalancers loadBalancers, @SuppressWarnings("unused") Lock lock) {
        var policies = new LinkedHashMap<>(get(loadBalancers.application));
        for (LoadBalancer loadBalancer : loadBalancers.list) {
            var policyId = new RoutingPolicyId(loadBalancer.application(), loadBalancer.cluster(), loadBalancers.zone);
            var existingPolicy = policies.get(policyId);
            var newPolicy = new RoutingPolicy(policyId, loadBalancer.hostname(), loadBalancer.dnsZone(),
                                              loadBalancers.endpointIdsOf(loadBalancer),
                                              new Status(isActive(loadBalancer), GlobalRouting.DEFAULT_STATUS));
            // Preserve global routing status for existing policy
            if (existingPolicy != null) {
                newPolicy = newPolicy.with(newPolicy.status().with(existingPolicy.status().globalRouting()));
            }
            updateZoneDnsOf(newPolicy);
            policies.put(newPolicy.id(), newPolicy);
        }
        db.writeRoutingPolicies(loadBalancers.application, policies);
    }

    /** Update zone DNS record for given policy */
    private void updateZoneDnsOf(RoutingPolicy policy) {
        var name = RecordName.from(policy.endpointIn(controller.system()).dnsName());
        var data = RecordData.fqdn(policy.canonicalName().value());
        controller.nameServiceForwarder().createCname(name, data, Priority.normal);
    }

    /** Remove policies and zone DNS records unreferenced by given load balancers */
    private void removePoliciesUnreferencedBy(AllocatedLoadBalancers loadBalancers, @SuppressWarnings("unused") Lock lock) {
        var policies = get(loadBalancers.application);
        var newPolicies = new LinkedHashMap<>(policies);
        var activeLoadBalancers = loadBalancers.list.stream().map(LoadBalancer::hostname).collect(Collectors.toSet());
        for (var policy : policies.values()) {
            // Leave active load balancers and irrelevant zones alone
            if (activeLoadBalancers.contains(policy.canonicalName()) ||
                !policy.id().zone().equals(loadBalancers.zone)) continue;

            var dnsName = policy.endpointIn(controller.system()).dnsName();
            controller.nameServiceForwarder().removeRecords(Record.Type.CNAME, RecordName.from(dnsName), Priority.normal);
            newPolicies.remove(policy.id());
        }
        db.writeRoutingPolicies(loadBalancers.application, newPolicies);
    }

    /** Remove unreferenced global endpoints from DNS */
    private void removeGlobalDnsUnreferencedBy(AllocatedLoadBalancers loadBalancers, @SuppressWarnings("unused") Lock lock) {
        var zonePolicies = get(loadBalancers.application, loadBalancers.zone).values();
        var removalCandidates = new HashSet<>(routingTableFrom(zonePolicies).keySet());
        var activeRoutingIds = routingIdsFrom(loadBalancers);
        removalCandidates.removeAll(activeRoutingIds);
        for (var id : removalCandidates) {
            var endpoint = RoutingPolicy.globalEndpointOf(id.application(), id.endpointId(), controller.system());
            controller.nameServiceForwarder().removeRecords(Record.Type.ALIAS, RecordName.from(endpoint.dnsName()), Priority.normal);
        }
    }

    /** Compute routing IDs from given load balancers */
    private static Set<RoutingId> routingIdsFrom(AllocatedLoadBalancers loadBalancers) {
        Set<RoutingId> routingIds = new LinkedHashSet<>();
        for (var loadBalancer : loadBalancers.list) {
            for (var endpointId : loadBalancers.endpointIdsOf(loadBalancer)) {
                routingIds.add(new RoutingId(loadBalancer.application(), endpointId));
            }
        }
        return Collections.unmodifiableSet(routingIds);
    }

    /** Compute a routing table from given policies */
    private static Map<RoutingId, List<RoutingPolicy>> routingTableFrom(Collection<RoutingPolicy> routingPolicies) {
        var routingTable = new LinkedHashMap<RoutingId, List<RoutingPolicy>>();
        for (var policy : routingPolicies) {
            for (var endpoint : policy.endpoints()) {
                var id = new RoutingId(policy.id().owner(), endpoint);
                routingTable.putIfAbsent(id, new ArrayList<>());
                routingTable.get(id).add(policy);
            }
        }
        return Collections.unmodifiableMap(routingTable);
    }

    private static boolean anyOut(GlobalRouting... globalRouting) {
        return Arrays.stream(globalRouting)
                     .map(GlobalRouting::status)
                     .anyMatch(status -> status == GlobalRouting.Status.out);
    }

    private static boolean isActive(LoadBalancer loadBalancer) {
        switch (loadBalancer.state()) {
            case reserved: // Count reserved as active as we want callers (application API) to see the endpoint as early
                           // as possible
            case active: return true;
        }
        return false;
    }

    /** Load balancers allocated to a deployment */
    private static class AllocatedLoadBalancers {

        private final ApplicationId application;
        private final ZoneId zone;
        private final List<LoadBalancer> list;
        private final DeploymentSpec deploymentSpec;

        private AllocatedLoadBalancers(ApplicationId application, ZoneId zone, List<LoadBalancer> loadBalancers,
                                       DeploymentSpec deploymentSpec) {
            this.application = application;
            this.zone = zone;
            this.list = List.copyOf(loadBalancers);
            this.deploymentSpec = deploymentSpec;
        }

        /** Compute all endpoint IDs for given load balancer */
        private Set<EndpointId> endpointIdsOf(LoadBalancer loadBalancer) {
            if (zone.environment().isManuallyDeployed()) { // Manual deployments do not have any configurable endpoints
                return Set.of();
            }
            var instanceSpec = deploymentSpec.instance(loadBalancer.application().instance());
            if (instanceSpec.isEmpty()) {
                return Set.of();
            }
            return instanceSpec.get().endpoints().stream()
                               .filter(endpoint -> endpoint.containerId().equals(loadBalancer.cluster().value()))
                               .filter(endpoint -> endpoint.regions().contains(zone.region()))
                               .map(com.yahoo.config.application.api.Endpoint::endpointId)
                               .map(EndpointId::of)
                               .collect(Collectors.toSet());
        }

    }

    /** Returns zones where global routing is declared inactive for instance through deploymentSpec */
    private static Set<ZoneId> inactiveZones(ApplicationId instance, DeploymentSpec deploymentSpec) {
        var instanceSpec = deploymentSpec.instance(instance.instance());
        if (instanceSpec.isEmpty()) return Set.of();
        return instanceSpec.get().zones().stream()
                           .filter(zone -> zone.environment().isProduction())
                           .filter(zone -> !zone.active())
                           .map(zone -> ZoneId.from(zone.environment(), zone.region().get()))
                           .collect(Collectors.toUnmodifiableSet());
    }

}

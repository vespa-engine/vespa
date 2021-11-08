// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.AliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.LatencyAliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.dns.WeightedAliasTarget;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.EndpointList;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.dns.NameServiceForwarder;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.dns.NameServiceRequest;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Updates routing policies and their associated DNS records based on a deployment's load balancers.
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
        return db.readRoutingPolicies(deployment.applicationId()).entrySet()
                 .stream()
                 .filter(kv -> kv.getKey().zone().equals(deployment.zoneId()))
                 .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Read routing policy for given zone */
    public ZoneRoutingPolicy get(ZoneId zone) {
        return db.readZoneRoutingPolicy(zone);
    }

    /**
     * Refresh routing policies for instance in given zone. This is idempotent and changes will only be performed if
     * load balancers for given instance have changed.
     */
    public void refresh(ApplicationId instance, DeploymentSpec deploymentSpec, ZoneId zone) {
        LoadBalancerAllocation allocation = new LoadBalancerAllocation(instance, zone, controller.serviceRegistry().configServer()
                                                                                                    .getLoadBalancers(instance, zone),
                                                                       deploymentSpec);
        Set<ZoneId> inactiveZones = inactiveZones(instance, deploymentSpec);
        try (var lock = db.lockRoutingPolicies()) {
            removeGlobalDnsUnreferencedBy(allocation, lock);
            removeApplicationDnsUnreferencedBy(allocation, lock);

            storePoliciesOf(allocation, lock);
            removePoliciesUnreferencedBy(allocation, lock);

            Collection<RoutingPolicy> policies = get(allocation.deployment.applicationId()).values();
            updateGlobalDnsOf(policies, inactiveZones, lock);
            updateApplicationDnsOf(policies, inactiveZones, lock);
        }
    }

    /** Set the status of all global endpoints in given zone */
    public void setRoutingStatus(ZoneId zone, RoutingStatus.Value value) {
        try (var lock = db.lockRoutingPolicies()) {
            db.writeZoneRoutingPolicy(new ZoneRoutingPolicy(zone, RoutingStatus.create(value, RoutingStatus.Agent.operator,
                                                                                       controller.clock().instant())));
            Map<ApplicationId, Map<RoutingPolicyId, RoutingPolicy>> allPolicies = db.readRoutingPolicies();
            for (var applicationPolicies : allPolicies.values()) {
                updateGlobalDnsOf(applicationPolicies.values(), Set.of(), lock);
            }
        }
    }

    /** Set the status of all global endpoints for given deployment */
    public void setRoutingStatus(DeploymentId deployment, RoutingStatus.Value value, RoutingStatus.Agent agent) {
        try (var lock = db.lockRoutingPolicies()) {
            var policies = get(deployment.applicationId());
            var newPolicies = new LinkedHashMap<>(policies);
            for (var policy : policies.values()) {
                if (!policy.appliesTo(deployment)) continue;
                var newPolicy = policy.with(policy.status().with(RoutingStatus.create(value, agent,
                                                                                      controller.clock().instant())));
                newPolicies.put(policy.id(), newPolicy);
            }
            db.writeRoutingPolicies(deployment.applicationId(), newPolicies);
            updateGlobalDnsOf(newPolicies.values(), Set.of(), lock);
            updateApplicationDnsOf(newPolicies.values(), Set.of(), lock);
        }
    }

    /** Update global DNS records for given policies */
    private void updateGlobalDnsOf(Collection<RoutingPolicy> routingPolicies, Set<ZoneId> inactiveZones, @SuppressWarnings("unused") Lock lock) {
        Map<RoutingId, List<RoutingPolicy>> routingTable = instanceRoutingTable(routingPolicies);
        for (Map.Entry<RoutingId, List<RoutingPolicy>> routeEntry : routingTable.entrySet()) {
            RoutingId routingId = routeEntry.getKey();
            controller.routing().readDeclaredEndpointsOf(routingId.instance())
                      .named(routingId.endpointId())
                      .not().requiresRotation()
                      .forEach(endpoint -> updateGlobalDnsOf(endpoint, inactiveZones, routeEntry.getValue()));
        }
    }

    /** Update global DNS records for given global endpoint */
    private void updateGlobalDnsOf(Endpoint endpoint, Set<ZoneId> inactiveZones, List<RoutingPolicy> policies) {
        if (endpoint.scope() != Endpoint.Scope.global) throw new IllegalArgumentException("Endpoint " + endpoint + " is not global");
        // Create a weighted ALIAS per region, pointing to all zones within the same region
        Collection<RegionEndpoint> regionEndpoints = computeRegionEndpoints(policies, inactiveZones);
        regionEndpoints.forEach(regionEndpoint -> {
            controller.nameServiceForwarder().createAlias(RecordName.from(regionEndpoint.target().name().value()),
                                                          Collections.unmodifiableSet(regionEndpoint.zoneTargets()),
                                                          Priority.normal);
        });

        // Create global latency-based ALIAS pointing to each per-region weighted ALIAS
        Set<AliasTarget> latencyTargets = new LinkedHashSet<>();
        Set<AliasTarget> inactiveLatencyTargets = new LinkedHashSet<>();
        for (var regionEndpoint : regionEndpoints) {
            if (regionEndpoint.active()) {
                latencyTargets.add(regionEndpoint.target());
            } else {
                inactiveLatencyTargets.add(regionEndpoint.target());
            }
        }

        // If all targets are configured OUT, all targets are kept IN. We do this because otherwise removing 100% of
        // the ALIAS records would cause the global endpoint to stop resolving entirely (NXDOMAIN).
        if (latencyTargets.isEmpty() && !inactiveLatencyTargets.isEmpty()) {
            latencyTargets.addAll(inactiveLatencyTargets);
            inactiveLatencyTargets.clear();
        }

        controller.nameServiceForwarder().createAlias(RecordName.from(endpoint.dnsName()), latencyTargets, Priority.normal);
        inactiveLatencyTargets.forEach(t -> controller.nameServiceForwarder()
                                                      .removeRecords(Record.Type.ALIAS,
                                                                     RecordData.fqdn(t.name().value()),
                                                                     Priority.normal));
    }

    /** Compute region endpoints and their targets from given policies */
    private Collection<RegionEndpoint> computeRegionEndpoints(List<RoutingPolicy> policies, Set<ZoneId> inactiveZones) {
        Map<Endpoint, RegionEndpoint> endpoints = new LinkedHashMap<>();
        RoutingMethod routingMethod = RoutingMethod.exclusive;
        for (var policy : policies) {
            if (policy.dnsZone().isEmpty()) continue;
            if (!controller.zoneRegistry().routingMethods(policy.id().zone()).contains(routingMethod)) continue;
            Endpoint regionEndpoint = policy.regionEndpointIn(controller.system(), routingMethod);
            var zonePolicy = db.readZoneRoutingPolicy(policy.id().zone());
            long weight = 1;
            if (isConfiguredOut(zonePolicy, policy, inactiveZones)) {
                weight = 0; // A record with 0 weight will not receive traffic. If all records within a group have 0
                            // weight, traffic is routed to all records with equal probability.
            }
            var weightedTarget = new WeightedAliasTarget(policy.canonicalName(), policy.dnsZone().get(),
                                                         policy.id().zone(), weight);
            endpoints.computeIfAbsent(regionEndpoint, (k) -> new RegionEndpoint(new LatencyAliasTarget(HostName.from(regionEndpoint.dnsName()),
                                                                                                       policy.dnsZone().get(),
                                                                                                       policy.id().zone())))
                     .zoneTargets()
                     .add(weightedTarget);
        }
        return endpoints.values();
    }


    private void updateApplicationDnsOf(Collection<RoutingPolicy> routingPolicies, Set<ZoneId> inactiveZones, @SuppressWarnings("unused") Lock lock) {
        // In the context of single deployment (which this is) there is only one routing policy per routing ID. I.e.
        // there is no scenario where more than one deployment within an instance can be a member the same
        // application-level endpoint. However, to allow this in the future the routing table remains
        // Map<RoutingId, List<RoutingPolicy>> instead of Map<RoutingId, RoutingPolicy>.
        Map<RoutingId, List<RoutingPolicy>> routingTable = applicationRoutingTable(routingPolicies);
        Map<String, Set<AliasTarget>> targetsByEndpoint = new LinkedHashMap<>();
        for (Map.Entry<RoutingId, List<RoutingPolicy>> routeEntry : routingTable.entrySet()) {
            RoutingId routingId = routeEntry.getKey();
            EndpointList endpoints = controller.routing().readDeclaredEndpointsOf(routingId.application())
                                               .scope(Endpoint.Scope.application)
                                               .named(routingId.endpointId());
            if (endpoints.isEmpty()) continue;
            if (endpoints.size() > 1) {
                throw new IllegalArgumentException("Expected at most 1 endpoint with ID '" + routingId.endpointId() +
                                                   ", got " + endpoints.size());
            }
            Endpoint endpoint = endpoints.asList().get(0);
            for (var policy : routeEntry.getValue()) {
                for (var target : endpoint.targets()) {
                    if (!policy.appliesTo(target.deployment())) continue;
                    int weight = target.weight();
                    if (isConfiguredOut(policy, inactiveZones)) {
                        weight = 0;
                    }
                    WeightedAliasTarget weightedAliasTarget = new WeightedAliasTarget(policy.canonicalName(), policy.dnsZone().get(),
                                                                                      target.deployment().zoneId(), weight);
                    targetsByEndpoint.computeIfAbsent(endpoint.dnsName(), (k) -> new LinkedHashSet<>())
                                     .add(weightedAliasTarget);
                }
            }
        }
        targetsByEndpoint.forEach((applicationEndpoint, targets) -> {
            controller.nameServiceForwarder().createAlias(RecordName.from(applicationEndpoint), targets, Priority.normal);
        });
    }

    /** Store routing policies for given load balancers */
    private void storePoliciesOf(LoadBalancerAllocation allocation, @SuppressWarnings("unused") Lock lock) {
        var policies = new LinkedHashMap<>(get(allocation.deployment.applicationId()));
        for (LoadBalancer loadBalancer : allocation.loadBalancers) {
            if (loadBalancer.hostname().isEmpty()) continue;
            var policyId = new RoutingPolicyId(loadBalancer.application(), loadBalancer.cluster(), allocation.deployment.zoneId());
            var existingPolicy = policies.get(policyId);
            var newPolicy = new RoutingPolicy(policyId, loadBalancer.hostname().get(), loadBalancer.dnsZone(),
                                              allocation.instanceEndpointsOf(loadBalancer),
                                              allocation.applicationEndpointsOf(loadBalancer),
                                              new RoutingPolicy.Status(isActive(loadBalancer), RoutingStatus.DEFAULT));
            // Preserve global routing status for existing policy
            if (existingPolicy != null) {
                newPolicy = newPolicy.with(newPolicy.status().with(existingPolicy.status().routingStatus()));
            }
            updateZoneDnsOf(newPolicy);
            policies.put(newPolicy.id(), newPolicy);
        }
        db.writeRoutingPolicies(allocation.deployment.applicationId(), policies);
    }

    /** Update zone DNS record for given policy */
    private void updateZoneDnsOf(RoutingPolicy policy) {
        for (var endpoint : policy.zoneEndpointsIn(controller.system(), RoutingMethod.exclusive, controller.zoneRegistry())) {
            var name = RecordName.from(endpoint.dnsName());
            var data = RecordData.fqdn(policy.canonicalName().value());
            nameServiceForwarderIn(policy.id().zone()).createCname(name, data, Priority.normal);
        }
    }

    /** Remove policies and zone DNS records unreferenced by given load balancers */
    private void removePoliciesUnreferencedBy(LoadBalancerAllocation allocation, @SuppressWarnings("unused") Lock lock) {
        var policies = get(allocation.deployment.applicationId());
        var newPolicies = new LinkedHashMap<>(policies);
        var activeIds = allocation.asPolicyIds();
        for (var policy : policies.values()) {
            // Leave active load balancers and irrelevant zones alone
            if (activeIds.contains(policy.id()) || !policy.appliesTo(allocation.deployment)) continue;
            for (var endpoint : policy.zoneEndpointsIn(controller.system(), RoutingMethod.exclusive, controller.zoneRegistry())) {
                var dnsName = endpoint.dnsName();
                nameServiceForwarderIn(allocation.deployment.zoneId()).removeRecords(Record.Type.CNAME,
                                                                                     RecordName.from(dnsName),
                                                                                     Priority.normal);
            }
            newPolicies.remove(policy.id());
        }
        db.writeRoutingPolicies(allocation.deployment.applicationId(), newPolicies);
    }

    /** Remove unreferenced instance endpoints from DNS */
    private void removeGlobalDnsUnreferencedBy(LoadBalancerAllocation allocation, @SuppressWarnings("unused") Lock lock) {
        Collection<RoutingPolicy> zonePolicies = get(allocation.deployment).values();
        Set<RoutingId> removalCandidates = new HashSet<>(instanceRoutingTable(zonePolicies).keySet());
        Set<RoutingId> activeRoutingIds = instanceRoutingIds(allocation);
        removalCandidates.removeAll(activeRoutingIds);
        for (var id : removalCandidates) {
            EndpointList endpoints = controller.routing().readDeclaredEndpointsOf(id.instance())
                                               .not().requiresRotation()
                                               .named(id.endpointId());
            NameServiceForwarder forwarder = nameServiceForwarderIn(allocation.deployment.zoneId());
            // This removes all ALIAS records having this DNS name. There is no attempt to delete only the entry for the
            // affected zone. Instead, the correct set of records is (re)created by updateGlobalDnsOf
            endpoints.forEach(endpoint -> forwarder.removeRecords(Record.Type.ALIAS, RecordName.from(endpoint.dnsName()),
                                                                  Priority.normal));
        }
    }

    /** Remove unreferenced application endpoints in given allocation from DNS */
    private void removeApplicationDnsUnreferencedBy(LoadBalancerAllocation allocation, @SuppressWarnings("unused") Lock lock) {
        Collection<RoutingPolicy> zonePolicies = get(allocation.deployment).values();
        Map<RoutingId, List<RoutingPolicy>> routingTable = applicationRoutingTable(zonePolicies);
        Set<RoutingId> removalCandidates = new HashSet<>(routingTable.keySet());
        Set<RoutingId> activeRoutingIds = applicationRoutingIds(allocation);
        removalCandidates.removeAll(activeRoutingIds);
        for (var id : removalCandidates) {
            TenantAndApplicationId application = TenantAndApplicationId.from(id.instance());
            EndpointList endpoints = controller.routing()
                                               .readDeclaredEndpointsOf(application)
                                               .named(id.endpointId());
            List<RoutingPolicy> policies = routingTable.get(id);
            for (var policy : policies) {
                if (!policy.appliesTo(allocation.deployment)) continue;
                NameServiceForwarder forwarder = nameServiceForwarderIn(policy.id().zone());
                endpoints.forEach(endpoint -> forwarder.removeRecords(Record.Type.ALIAS,
                                                                      RecordName.from(endpoint.dnsName()),
                                                                      RecordData.fqdn(policy.canonicalName().value()),
                                                                      Priority.normal));
            }
        }
    }

    private Set<RoutingId> instanceRoutingIds(LoadBalancerAllocation allocation) {
        return routingIdsFrom(allocation, false);
    }

    private Set<RoutingId> applicationRoutingIds(LoadBalancerAllocation allocation) {
        return routingIdsFrom(allocation, true);
    }

    /** Compute routing IDs from given load balancers */
    private static Set<RoutingId> routingIdsFrom(LoadBalancerAllocation allocation, boolean applicationLevel) {
        Set<RoutingId> routingIds = new LinkedHashSet<>();
        for (var loadBalancer : allocation.loadBalancers) {
            Set<EndpointId> endpoints = applicationLevel
                    ? allocation.applicationEndpointsOf(loadBalancer)
                    : allocation.instanceEndpointsOf(loadBalancer);
            for (var endpointId : endpoints) {
                routingIds.add(RoutingId.of(loadBalancer.application(), endpointId));
            }
        }
        return Collections.unmodifiableSet(routingIds);
    }

    /** Compute a routing table for instance-level endpoints from given policies */
    private static Map<RoutingId, List<RoutingPolicy>> instanceRoutingTable(Collection<RoutingPolicy> routingPolicies) {
        return routingTable(routingPolicies, false);
    }

    /** Compute a routing table for application-level endpoints from given policies */
    private static Map<RoutingId, List<RoutingPolicy>> applicationRoutingTable(Collection<RoutingPolicy> routingPolicies) {
        return routingTable(routingPolicies, true);
    }

    private static Map<RoutingId, List<RoutingPolicy>> routingTable(Collection<RoutingPolicy> routingPolicies, boolean applicationLevel) {
        Map<RoutingId, List<RoutingPolicy>> routingTable = new LinkedHashMap<>();
        for (var policy : routingPolicies) {
            Set<EndpointId> endpoints = applicationLevel ? policy.applicationEndpoints() : policy.instanceEndpoints();
            for (var endpoint : endpoints) {
                RoutingId id = RoutingId.of(policy.id().owner(), endpoint);
                routingTable.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(policy);
            }
        }
        return Collections.unmodifiableMap(routingTable);
    }

    /** Returns whether the endpoints of given policy are globally configured {@link RoutingStatus.Value#out} */
    private static boolean isConfiguredOut(ZoneRoutingPolicy zonePolicy, RoutingPolicy policy, Set<ZoneId> inactiveZones) {
        return isConfiguredOut(policy, Optional.of(zonePolicy), inactiveZones);
    }

    /** Returns whether the endpoints of given policy are configured {@link RoutingStatus.Value#out} */
    private static boolean isConfiguredOut(RoutingPolicy policy, Set<ZoneId> inactiveZones) {
        return isConfiguredOut(policy, Optional.empty(), inactiveZones);
    }

    private static boolean isConfiguredOut(RoutingPolicy policy, Optional<ZoneRoutingPolicy> zonePolicy, Set<ZoneId> inactiveZones) {
        // A deployment can be configured out from endpoints at any of the following levels:
        // - zone level (ZoneRoutingPolicy, only applies to global endpoints)
        // - deployment level (RoutingPolicy)
        // - application package level (deployment.xml)
        return (zonePolicy.isPresent() && zonePolicy.get().routingStatus().value() == RoutingStatus.Value.out) ||
               policy.status().routingStatus().value() == RoutingStatus.Value.out ||
               inactiveZones.contains(policy.id().zone());
    }

    private static boolean isActive(LoadBalancer loadBalancer) {
        switch (loadBalancer.state()) {
            case reserved: // Count reserved as active as we want callers (application API) to see the endpoint as early
                           // as possible
            case active: return true;
        }
        return false;
    }

    /** Represents records for a region-wide endpoint */
    private static class RegionEndpoint {

        private final LatencyAliasTarget target;
        private final Set<WeightedAliasTarget> zoneTargets = new LinkedHashSet<>();

        public RegionEndpoint(LatencyAliasTarget target) {
            this.target = Objects.requireNonNull(target);
        }

        public LatencyAliasTarget target() {
            return target;
        }

        public Set<WeightedAliasTarget> zoneTargets() {
            return zoneTargets;
        }

        public boolean active() {
            return zoneTargets.stream().anyMatch(target -> target.weight() > 0);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegionEndpoint that = (RegionEndpoint) o;
            return target.name().equals(that.target.name());
        }

        @Override
        public int hashCode() {
            return Objects.hash(target.name());
        }

    }

    /** Load balancers allocated to a deployment */
    private static class LoadBalancerAllocation {

        private final DeploymentId deployment;
        private final List<LoadBalancer> loadBalancers;
        private final DeploymentSpec deploymentSpec;

        private LoadBalancerAllocation(ApplicationId application, ZoneId zone, List<LoadBalancer> loadBalancers,
                                       DeploymentSpec deploymentSpec) {
            this.deployment = new DeploymentId(application, zone);
            this.loadBalancers = List.copyOf(loadBalancers);
            this.deploymentSpec = deploymentSpec;
        }

        /** Returns the policy IDs of the load balancers contained in this */
        private Set<RoutingPolicyId> asPolicyIds() {
            return loadBalancers.stream()
                                .map(lb -> new RoutingPolicyId(lb.application(),
                                                               lb.cluster(),
                                                               deployment.zoneId()))
                                .collect(Collectors.toUnmodifiableSet());
        }

        /** Returns all instance endpoint IDs served by given load balancer */
        private Set<EndpointId> instanceEndpointsOf(LoadBalancer loadBalancer) {
            if (!deployment.zoneId().environment().isProduction()) { // Only production deployments have configurable endpoints
                return Set.of();
            }
            var instanceSpec = deploymentSpec.instance(loadBalancer.application().instance());
            if (instanceSpec.isEmpty()) {
                return Set.of();
            }
            if (instanceSpec.get().globalServiceId().filter(id -> id.equals(loadBalancer.cluster().value())).isPresent()) {
                // Legacy assignment always has the default endpoint Id
                return Set.of(EndpointId.defaultId());
            }
            return instanceSpec.get().endpoints().stream()
                               .filter(endpoint -> endpoint.containerId().equals(loadBalancer.cluster().value()))
                               .filter(endpoint -> endpoint.regions().contains(deployment.zoneId().region()))
                               .map(com.yahoo.config.application.api.Endpoint::endpointId)
                               .map(EndpointId::of)
                               .collect(Collectors.toUnmodifiableSet());
        }

        /** Returns all application endpoint IDs served by given load balancer */
        private Set<EndpointId> applicationEndpointsOf(LoadBalancer loadBalancer) {
            if (!deployment.zoneId().environment().isProduction()) { // Only production deployments have configurable endpoints
                return Set.of();
            }
            return deploymentSpec.endpoints().stream()
                                 .filter(endpoint -> endpoint.containerId().equals(loadBalancer.cluster().value()))
                                 .filter(endpoint -> endpoint.targets().stream()
                                                             .anyMatch(target -> target.region().equals(deployment.zoneId().region()) &&
                                                                                 target.instance().equals(deployment.applicationId().instance())))
                                 .map(com.yahoo.config.application.api.Endpoint::endpointId)
                                 .map(EndpointId::of)
                                 .collect(Collectors.toUnmodifiableSet());
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

    /** Returns the name updater to use for given zone */
    private NameServiceForwarder nameServiceForwarderIn(ZoneId zone) {
        if (controller.zoneRegistry().routingMethods(zone).contains(RoutingMethod.exclusive)) {
            return controller.nameServiceForwarder();
        }
        return new NameServiceDiscarder(controller.curator());
    }

    /** A {@link NameServiceForwarder} that does nothing. Used in zones where no explicit DNS updates are needed */
    private static class NameServiceDiscarder extends NameServiceForwarder {

        public NameServiceDiscarder(CuratorDb db) {
            super(db);
        }

        @Override
        protected void forward(NameServiceRequest request, Priority priority) {
            // Ignored
        }
    }

}

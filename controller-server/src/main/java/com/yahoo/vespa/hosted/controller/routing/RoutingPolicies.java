// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import ai.vespa.http.DomainName;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.ClusterId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.AliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.DirectTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.LatencyAliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record.Type;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.dns.VpcEndpointService.DnsChallenge;
import com.yahoo.vespa.hosted.controller.api.integration.dns.VpcEndpointService.ChallengeState;
import com.yahoo.vespa.hosted.controller.api.integration.dns.WeightedAliasTarget;
import com.yahoo.vespa.hosted.controller.api.integration.dns.WeightedDirectTarget;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Updates routing policies and their associated DNS records based on a deployment's load balancers.
 *
 * @author mortent
 * @author mpolden
 */
public class RoutingPolicies {

    private static final Logger LOG = Logger.getLogger(RoutingPolicies.class.getName());

    private final Controller controller;
    private final CuratorDb db;
    private final BooleanFlag createTokenEndpoint;

    public RoutingPolicies(Controller controller) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
        this.db = controller.curator();
        this.createTokenEndpoint = Flags.ENABLE_DATAPLANE_PROXY.bindTo(controller.flagSource());
        try (var lock = db.lockRoutingPolicies()) { // Update serialized format
            for (var policy : db.readRoutingPolicies().entrySet()) {
                db.writeRoutingPolicies(policy.getKey(), policy.getValue());
            }
        }
    }

    /** Read all routing policies for given deployment */
    public RoutingPolicyList read(DeploymentId deployment) {
        return read(deployment.applicationId()).deployment(deployment);
    }

    /** Read all routing policies for given instance */
    public RoutingPolicyList read(ApplicationId instance) {
        return RoutingPolicyList.copyOf(db.readRoutingPolicies(instance));
    }

    /** Read all routing policies for given application */
    private RoutingPolicyList read(TenantAndApplicationId application) {
        return db.readRoutingPolicies((instance) -> TenantAndApplicationId.from(instance).equals(application))
                 .values()
                 .stream()
                 .flatMap(Collection::stream)
                 .collect(Collectors.collectingAndThen(Collectors.toList(), RoutingPolicyList::copyOf));
    }

    /** Read all routing policies */
    private RoutingPolicyList readAll() {
        return db.readRoutingPolicies()
                 .values()
                 .stream()
                 .flatMap(Collection::stream)
                 .collect(Collectors.collectingAndThen(Collectors.toList(), RoutingPolicyList::copyOf));
    }

    /** Read routing policy for given zone */
    public ZoneRoutingPolicy read(ZoneId zone) {
        return db.readZoneRoutingPolicy(zone);
    }

    /**
     * Refresh routing policies for instance in given zone. This is idempotent and changes will only be performed if
     * routing configuration affecting given deployment has changed.
     */
    public void refresh(DeploymentId deployment, DeploymentSpec deploymentSpec) {
        ApplicationId instance = deployment.applicationId();
        List<LoadBalancer> loadBalancers = controller.serviceRegistry().configServer()
                                                     .getLoadBalancers(instance, deployment.zoneId());
        LoadBalancerAllocation allocation = new LoadBalancerAllocation(deployment, deploymentSpec, loadBalancers);
        Set<ZoneId> inactiveZones = inactiveZones(instance, deploymentSpec);
        Optional<TenantAndApplicationId> owner = ownerOf(allocation);
        try (var lock = db.lockRoutingPolicies()) {
            RoutingPolicyList applicationPolicies = read(TenantAndApplicationId.from(instance));
            RoutingPolicyList instancePolicies = applicationPolicies.instance(instance);
            RoutingPolicyList deploymentPolicies = applicationPolicies.deployment(allocation.deployment);

            removeGlobalDnsUnreferencedBy(allocation, deploymentPolicies, lock);
            removeApplicationDnsUnreferencedBy(allocation, deploymentPolicies, lock);

            instancePolicies = storePoliciesOf(allocation, instancePolicies, lock);
            instancePolicies = removePoliciesUnreferencedBy(allocation, instancePolicies, lock);

            applicationPolicies = applicationPolicies.replace(instance, instancePolicies);
            updateGlobalDnsOf(instancePolicies, Optional.of(deployment), inactiveZones, owner, lock);
            updateApplicationDnsOf(applicationPolicies, inactiveZones, deployment, owner, lock);
        }
    }

    /** Set the status of all global endpoints in given zone */
    public void setRoutingStatus(ZoneId zone, RoutingStatus.Value value) {
        try (var lock = db.lockRoutingPolicies()) {
            db.writeZoneRoutingPolicy(new ZoneRoutingPolicy(zone, RoutingStatus.create(value, RoutingStatus.Agent.operator,
                                                                                       controller.clock().instant())));
            Map<ApplicationId, RoutingPolicyList> allPolicies = readAll().groupingBy(policy -> policy.id().owner());
            allPolicies.forEach((instance, policies) -> {
                updateGlobalDnsOf(policies, Optional.empty(), Set.of(), Optional.of(TenantAndApplicationId.from(instance)), lock);
            });
        }
    }

    /** Set the status of all global endpoints for given deployment */
    public void setRoutingStatus(DeploymentId deployment, RoutingStatus.Value value, RoutingStatus.Agent agent) {
        ApplicationId instance = deployment.applicationId();
        try (var lock = db.lockRoutingPolicies()) {
            RoutingPolicyList applicationPolicies = read(TenantAndApplicationId.from(instance));
            RoutingPolicyList deploymentPolicies = applicationPolicies.deployment(deployment);
            Map<RoutingPolicyId, RoutingPolicy> updatedPolicies = new LinkedHashMap<>(applicationPolicies.asMap());
            for (var policy : deploymentPolicies) {
                var newPolicy = policy.with(RoutingStatus.create(value, agent, controller.clock().instant()));
                updatedPolicies.put(policy.id(), newPolicy);
            }
            RoutingPolicyList effectivePolicies = RoutingPolicyList.copyOf(updatedPolicies.values());
            Map<ApplicationId, RoutingPolicyList> policiesByInstance = effectivePolicies.groupingBy(policy -> policy.id().owner());
            policiesByInstance.forEach((ignored, instancePolicies) -> updateGlobalDnsOf(instancePolicies,
                                                                                        Optional.of(deployment),
                                                                                        Set.of(),
                                                                                        ownerOf(deployment),
                                                                                        lock));
            updateApplicationDnsOf(effectivePolicies, Set.of(), deployment, ownerOf(deployment), lock);
            policiesByInstance.forEach((owner, instancePolicies) -> db.writeRoutingPolicies(owner, instancePolicies.asList()));
        }
    }

    /** Update global DNS records for given policies */
    private void updateGlobalDnsOf(RoutingPolicyList instancePolicies, Optional<DeploymentId> deployment,
                                   Set<ZoneId> inactiveZones, Optional<TenantAndApplicationId> owner,
                                   @SuppressWarnings("unused") Mutex lock) {
        Map<RoutingId, List<RoutingPolicy>> routingTable = instancePolicies.asInstanceRoutingTable();
        for (Map.Entry<RoutingId, List<RoutingPolicy>> routeEntry : routingTable.entrySet()) {
            RoutingId routingId = routeEntry.getKey();
            controller.routing().readDeclaredEndpointsOf(routingId.instance())
                      .named(routingId.endpointId(), Endpoint.Scope.global)
                      .not().requiresRotation()
                      .forEach(endpoint -> updateGlobalDnsOf(endpoint, inactiveZones, routeEntry.getValue(), deployment, owner));
        }
    }

    /** Update global DNS records for given global endpoint */
    private void updateGlobalDnsOf(Endpoint endpoint, Set<ZoneId> inactiveZones, List<RoutingPolicy> policies,
                                   Optional<DeploymentId> deployment, Optional<TenantAndApplicationId> owner) {
        if (endpoint.scope() != Endpoint.Scope.global) throw new IllegalArgumentException("Endpoint " + endpoint + " is not global");
        if (deployment.isPresent() && !endpoint.deployments().contains(deployment.get())) return;

        Collection<RegionEndpoint> regionEndpoints = computeRegionEndpoints(policies, inactiveZones);
        Set<AliasTarget> latencyTargets = new LinkedHashSet<>();
        Set<AliasTarget> inactiveLatencyTargets = new LinkedHashSet<>();
        for (var regionEndpoint : regionEndpoints) {
            if (regionEndpoint.active()) {
                latencyTargets.add(regionEndpoint.target());
            } else {
                inactiveLatencyTargets.add(regionEndpoint.target());
            }
        }

        // Refuse removal of last target in an endpoint. We do this because removing 100% of the ALIAS records would
        // cause the application endpoint to stop resolving entirely (NXDOMAIN).
        if (latencyTargets.isEmpty() && !inactiveLatencyTargets.isEmpty()) {
            if (deployment.isPresent()) {
                throw new IllegalArgumentException("Cannot deactivate routing for " + deployment.get() +
                                                   " as it's the last remaining active deployment in " + endpoint);
            } else {
                // Operator is deactivating routing for entire zone, but this endpoint only has one target
                LOG.log(Level.WARNING, "Cannot deactivate routing for " + endpoint + " because it has only one " +
                                       "active zone. Leaving it in");
                return;
            }
        }

        // Create a weighted ALIAS per region, pointing to all zones within the same region
        regionEndpoints.forEach(regionEndpoint -> {
            if ( ! regionEndpoint.zoneAliasTargets().isEmpty()) {
                controller.nameServiceForwarder().createAlias(RecordName.from(regionEndpoint.target().name().value()),
                                                              regionEndpoint.zoneAliasTargets(),
                                                              Priority.normal,
                                                              owner);
            }
            if ( ! regionEndpoint.zoneDirectTargets().isEmpty()) {
                controller.nameServiceForwarder().createDirect(RecordName.from(regionEndpoint.target().name().value()),
                                                               regionEndpoint.zoneDirectTargets(),
                                                               Priority.normal,
                                                               owner);
            }
        });

        // Create global latency-based ALIAS pointing to each per-region weighted ALIAS
        controller.nameServiceForwarder().createAlias(RecordName.from(endpoint.dnsName()), latencyTargets, Priority.normal, owner);
        inactiveLatencyTargets.forEach(t -> controller.nameServiceForwarder()
                                                      .removeRecords(Record.Type.ALIAS,
                                                                     RecordName.from(endpoint.dnsName()),
                                                                     RecordData.from(t.name().value()),
                                                                     Priority.normal,
                                                                     owner));
    }

    /** Compute region endpoints and their targets from given policies */
    private Collection<RegionEndpoint> computeRegionEndpoints(List<RoutingPolicy> policies, Set<ZoneId> inactiveZones) {
        Map<Endpoint, RegionEndpoint> endpoints = new LinkedHashMap<>();
        for (var policy : policies) {
            if (policy.dnsZone().isEmpty() && policy.canonicalName().isPresent()) continue;
            if (controller.zoneRegistry().routingMethod(policy.id().zone()) != RoutingMethod.exclusive) continue;
            Endpoint endpoint = policy.regionEndpointIn(controller.system(), RoutingMethod.exclusive);
            var zonePolicy = db.readZoneRoutingPolicy(policy.id().zone());
            long weight = 1;
            if (isConfiguredOut(zonePolicy, policy, inactiveZones)) {
                weight = 0; // A record with 0 weight will not receive traffic. If all records within a group have 0
                            // weight, traffic is routed to all records with equal probability.
            }

            RegionEndpoint regionEndpoint = endpoints.computeIfAbsent(endpoint, (k) -> new RegionEndpoint(
                    new LatencyAliasTarget(DomainName.of(endpoint.dnsName()), policy.dnsZone().get(), policy.id().zone())));

            if (policy.canonicalName().isPresent()) {
                var weightedTarget = new WeightedAliasTarget(
                        policy.canonicalName().get(), policy.dnsZone().get(), policy.id().zone().value(), weight);
                regionEndpoint.add(weightedTarget);
            } else {
                var weightedTarget = new WeightedDirectTarget(
                        RecordData.from(policy.ipAddress().get()), policy.id().zone(), weight);
                regionEndpoint.add(weightedTarget);
            }
        }
        return endpoints.values();
    }


    private void updateApplicationDnsOf(RoutingPolicyList routingPolicies, Set<ZoneId> inactiveZones,
                                        DeploymentId deployment, Optional<TenantAndApplicationId> owner,
                                        @SuppressWarnings("unused") Mutex lock) {
        // In the context of single deployment (which this is) there is only one routing policy per routing ID. I.e.
        // there is no scenario where more than one deployment within an instance can be a member the same
        // application-level endpoint. However, to allow this in the future the routing table remains
        // Map<RoutingId, List<RoutingPolicy>> instead of Map<RoutingId, RoutingPolicy>.
        Map<RoutingId, List<RoutingPolicy>> routingTable = routingPolicies.asApplicationRoutingTable();
        if (routingTable.isEmpty()) return;

        Application application = controller.applications().requireApplication(routingTable.keySet().iterator().next().application());
        Map<Endpoint, Set<Target>> targetsByEndpoint = new LinkedHashMap<>();
        Map<Endpoint, Set<Target>> inactiveTargetsByEndpoint = new LinkedHashMap<>();
        for (Map.Entry<RoutingId, List<RoutingPolicy>> routeEntry : routingTable.entrySet()) {
            RoutingId routingId = routeEntry.getKey();
            EndpointList endpoints = controller.routing().declaredEndpointsOf(application)
                                               .named(routingId.endpointId(), Endpoint.Scope.application);
            for (Endpoint endpoint : endpoints) {
                for (var policy : routeEntry.getValue()) {
                    for (var target : endpoint.targets()) {
                        if (!policy.appliesTo(target.deployment())) continue;
                        if (policy.dnsZone().isEmpty() && policy.canonicalName().isPresent())
                            continue; // Does not support ALIAS records
                        ZoneRoutingPolicy zonePolicy = db.readZoneRoutingPolicy(policy.id().zone());

                        Set<Target> activeTargets = targetsByEndpoint.computeIfAbsent(endpoint, (k) -> new LinkedHashSet<>());
                        Set<Target> inactiveTargets = inactiveTargetsByEndpoint.computeIfAbsent(endpoint, (k) -> new LinkedHashSet<>());
                        if (isConfiguredOut(zonePolicy, policy, inactiveZones)) {
                            inactiveTargets.add(Target.weighted(policy, target));
                        } else {
                            activeTargets.add(Target.weighted(policy, target));
                        }
                    }
                }
            }
        }

        // Refuse removal of last target in an endpoint. We do this because removing 100% of the ALIAS records would
        // cause the application endpoint to stop resolving entirely (NXDOMAIN).
        targetsByEndpoint.forEach((endpoint, targets) -> {
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("Cannot deactivate routing for " + deployment +
                                                   " as it's the last remaining active deployment in " + endpoint);
            }
        });

        // Create DNS records for active targets
        targetsByEndpoint.forEach((applicationEndpoint, targets) -> {
            // Where multiple zones are permitted, they all have the same routing policy, and nameServiceForwarder (below).
            ZoneId targetZone = applicationEndpoint.targets().iterator().next().deployment().zoneId();
            Set<AliasTarget> aliasTargets = new LinkedHashSet<>();
            Set<DirectTarget> directTargets = new LinkedHashSet<>();
            for (Target target : targets) {
                if (!target.deployment().equals(deployment)) continue; // Do not update target not matching this deployment
                if (target.aliasOrDirectTarget() instanceof AliasTarget at) {
                    aliasTargets.add(at);
                } else {
                    directTargets.add((DirectTarget) target.aliasOrDirectTarget());
                }
            }
            if (!aliasTargets.isEmpty()) {
                nameServiceForwarderIn(targetZone).createAlias(
                        RecordName.from(applicationEndpoint.dnsName()), aliasTargets, Priority.normal, owner);
                nameServiceForwarderIn(targetZone).removeRecords(Type.ALIAS, RecordName.from(applicationEndpoint.legacyRegionalDnsName()),
                                                                 Priority.normal, owner);
            }
            if (!directTargets.isEmpty()) {
                nameServiceForwarderIn(targetZone).createDirect(
                        RecordName.from(applicationEndpoint.dnsName()), directTargets, Priority.normal, owner);
                nameServiceForwarderIn(targetZone).removeRecords(Type.DIRECT, RecordName.from(applicationEndpoint.legacyRegionalDnsName()),
                                                                 Priority.normal, owner);
            }
        });

        // Remove DNS records for inactive targets
        inactiveTargetsByEndpoint.forEach((applicationEndpoint, targets) -> {
            // Where multiple zones are permitted, they all have the same routing policy, and nameServiceForwarder.
            ZoneId targetZone = applicationEndpoint.targets().iterator().next().deployment().zoneId();
            targets.forEach(target -> {
                if (!target.deployment().equals(deployment)) return; // Do not update target not matching this deployment
                nameServiceForwarderIn(targetZone).removeRecords(target.type(),
                                                                 RecordName.from(applicationEndpoint.dnsName()),
                                                                 target.data(),
                                                                 Priority.normal,
                                                                 owner);
                // TODO(mpolden): Remove this and legacy name support in Endpoint after 2023-06-22
                nameServiceForwarderIn(targetZone).removeRecords(target.type(),
                                                                 RecordName.from(applicationEndpoint.legacyRegionalDnsName()),
                                                                 target.data(),
                                                                 Priority.normal,
                                                                 owner);
            });
        });
    }

    /**
     * Store routing policies for given load balancers
     *
     * @return the updated policies
     */
    private RoutingPolicyList storePoliciesOf(LoadBalancerAllocation allocation, RoutingPolicyList instancePolicies, @SuppressWarnings("unused") Mutex lock) {
        Map<RoutingPolicyId, RoutingPolicy> policies = new LinkedHashMap<>(instancePolicies.asMap());
        for (LoadBalancer loadBalancer : allocation.loadBalancers) {
            if (loadBalancer.hostname().isEmpty() && loadBalancer.ipAddress().isEmpty()) continue;
            var policyId = new RoutingPolicyId(loadBalancer.application(), loadBalancer.cluster(), allocation.deployment.zoneId());
            var existingPolicy = policies.get(policyId);
            var dnsZone = loadBalancer.ipAddress().isPresent() ? Optional.of("ignored") : loadBalancer.dnsZone();
            var newPolicy = new RoutingPolicy(policyId, loadBalancer.hostname(), loadBalancer.ipAddress(), dnsZone,
                                              allocation.instanceEndpointsOf(loadBalancer),
                                              allocation.applicationEndpointsOf(loadBalancer),
                                              RoutingStatus.DEFAULT,
                                              loadBalancer.isPublic());
            // Preserve global routing status for existing policy
            if (existingPolicy != null) {
                newPolicy = newPolicy.with(existingPolicy.routingStatus());
            }
            updateZoneDnsOf(newPolicy, loadBalancer, allocation.deployment);
            policies.put(newPolicy.id(), newPolicy);
        }
        RoutingPolicyList updated = RoutingPolicyList.copyOf(policies.values());
        db.writeRoutingPolicies(allocation.deployment.applicationId(), updated.asList());
        return updated;
    }

    /** Update zone DNS record for given policy */
    private void updateZoneDnsOf(RoutingPolicy policy, LoadBalancer loadBalancer, DeploymentId deploymentId) {
        boolean addTokenEndpoint = createTokenEndpoint.with(FetchVector.Dimension.APPLICATION_ID, deploymentId.applicationId().serializedForm()).value();
        for (var endpoint : policy.zoneEndpointsIn(controller.system(), RoutingMethod.exclusive, addTokenEndpoint)) {
            var name = RecordName.from(endpoint.dnsName());
            var record = policy.canonicalName().isPresent() ?
                    new Record(Record.Type.CNAME, name, RecordData.fqdn(policy.canonicalName().get().value())) :
                    new Record(Record.Type.A, name, RecordData.from(policy.ipAddress().orElseThrow()));
            nameServiceForwarderIn(policy.id().zone()).createRecord(record, Priority.normal, ownerOf(deploymentId));
            setPrivateDns(endpoint, loadBalancer, deploymentId);
        }
    }

    private void setPrivateDns(Endpoint endpoint, LoadBalancer loadBalancer, DeploymentId deploymentId) {
        if (loadBalancer.service().isEmpty()) return;
        if (endpoint.isTokenEndpoint()) return;
        controller.serviceRegistry().vpcEndpointService()
                  .setPrivateDns(DomainName.of(endpoint.dnsName()),
                                 new ClusterId(deploymentId, endpoint.cluster()),
                                 loadBalancer.cloudAccount())
                  .ifPresent(challenge -> {
                      try (Mutex lock = db.lockNameServiceQueue()) {
                          nameServiceForwarderIn(deploymentId.zoneId()).createTxt(challenge.name(), List.of(challenge.data()), Priority.high, ownerOf(deploymentId));
                          db.writeDnsChallenge(challenge);
                      }
                  });
    }

    /** Returns true iff. the given deployment has no incomplete DNS challenges, or throws (and cleans up) on errors. */
    public boolean processDnsChallenges(DeploymentId deploymentId) {
        try (Mutex lock = db.lockNameServiceQueue()) {
            List<DnsChallenge> challenges = new ArrayList<>(db.readDnsChallenges(deploymentId));
            Set<RecordName> pendingRequests = controller.curator().readNameServiceQueue().requests().stream()
                                                        .map(NameServiceRequest::name)
                                                        .collect(Collectors.toSet());
            try {
                challenges.removeIf(challenge -> {
                    if (challenge.state() == ChallengeState.pending) {
                        if (pendingRequests.contains(challenge.name())) return false;
                        challenge = challenge.withState(ChallengeState.ready);
                    }
                    ChallengeState state = controller.serviceRegistry().vpcEndpointService().process(challenge);
                    if (state == ChallengeState.done) {
                        removeDnsChallenge(challenge);
                        return true;
                    }
                    else {
                        db.writeDnsChallenge(challenge.withState(state));
                        return false;
                    }
                });
                return challenges.isEmpty();
            }
            catch (RuntimeException e) {
                challenges.forEach(this::removeDnsChallenge);
                throw e;
            }
        }
    }

    private void removeDnsChallenge(DnsChallenge challenge) {
        nameServiceForwarderIn(challenge.clusterId().deploymentId().zoneId())
                .removeRecords(Type.TXT, challenge.name(), Priority.normal, ownerOf(challenge.clusterId().deploymentId()));
        db.deleteDnsChallenge(challenge.clusterId());
    }

    /**
     * Remove policies and zone DNS records unreferenced by given load balancers
     *
     * @return the updated policies
     */
    private RoutingPolicyList removePoliciesUnreferencedBy(LoadBalancerAllocation allocation, RoutingPolicyList instancePolicies, @SuppressWarnings("unused") Mutex lock) {
        boolean addTokenEndpoint = createTokenEndpoint.with(FetchVector.Dimension.APPLICATION_ID, allocation.deployment.applicationId().serializedForm()).value();
        Map<RoutingPolicyId, RoutingPolicy> newPolicies = new LinkedHashMap<>(instancePolicies.asMap());
        Set<RoutingPolicyId> activeIds = allocation.asPolicyIds();
        RoutingPolicyList removable = instancePolicies.deployment(allocation.deployment)
                                                      .not().matching(policy -> activeIds.contains(policy.id()));
        for (var policy : removable) {
            for (var endpoint : policy.zoneEndpointsIn(controller.system(), RoutingMethod.exclusive, addTokenEndpoint)) {
                nameServiceForwarderIn(allocation.deployment.zoneId()).removeRecords(Record.Type.CNAME,
                                                                                     RecordName.from(endpoint.dnsName()),
                                                                                     Priority.normal,
                                                                                     ownerOf(allocation));
            }
            newPolicies.remove(policy.id());
        }
        RoutingPolicyList updated = RoutingPolicyList.copyOf(newPolicies.values());
        db.writeRoutingPolicies(allocation.deployment.applicationId(), updated.asList());
        return updated;
    }

    /** Remove unreferenced instance endpoints from DNS */
    private void removeGlobalDnsUnreferencedBy(LoadBalancerAllocation allocation, RoutingPolicyList deploymentPolicies, @SuppressWarnings("unused") Mutex lock) {
        Set<RoutingId> removalCandidates = new HashSet<>(deploymentPolicies.asInstanceRoutingTable().keySet());
        Set<RoutingId> activeRoutingIds = instanceRoutingIds(allocation);
        removalCandidates.removeAll(activeRoutingIds);
        for (var id : removalCandidates) {
            EndpointList endpoints = controller.routing().readDeclaredEndpointsOf(id.instance())
                                               .not().requiresRotation()
                                               .named(id.endpointId(), Endpoint.Scope.global);
            NameServiceForwarder forwarder = nameServiceForwarderIn(allocation.deployment.zoneId());
            // This removes all ALIAS records having this DNS name. There is no attempt to delete only the entry for the
            // affected zone. Instead, the correct set of records is (re)created by updateGlobalDnsOf
            endpoints.forEach(endpoint -> forwarder.removeRecords(Record.Type.ALIAS, RecordName.from(endpoint.dnsName()),
                                                                  Priority.normal,
                                                                  ownerOf(allocation)));
        }
    }

    /** Remove unreferenced application endpoints in given allocation from DNS */
    private void removeApplicationDnsUnreferencedBy(LoadBalancerAllocation allocation, RoutingPolicyList deploymentPolicies, @SuppressWarnings("unused") Mutex lock) {
        Map<RoutingId, List<RoutingPolicy>> routingTable = deploymentPolicies.asApplicationRoutingTable();
        Set<RoutingId> removalCandidates = new HashSet<>(routingTable.keySet());
        Set<RoutingId> activeRoutingIds = applicationRoutingIds(allocation);
        removalCandidates.removeAll(activeRoutingIds);
        for (var id : removalCandidates) {
            TenantAndApplicationId application = TenantAndApplicationId.from(id.instance());
            EndpointList endpoints = controller.routing()
                                               .readDeclaredEndpointsOf(application)
                                               .named(id.endpointId(), Endpoint.Scope.application);
            List<RoutingPolicy> policies = routingTable.get(id);
            for (var policy : policies) {
                if (!policy.appliesTo(allocation.deployment)) continue;
                NameServiceForwarder forwarder = nameServiceForwarderIn(policy.id().zone());
                for (Endpoint endpoint : endpoints) {
                    if (policy.canonicalName().isPresent()) {
                        forwarder.removeRecords(Record.Type.ALIAS,
                                                RecordName.from(endpoint.dnsName()),
                                                RecordData.fqdn(policy.canonicalName().get().value()),
                                                Priority.normal,
                                                ownerOf(allocation));
                        forwarder.removeRecords(Record.Type.ALIAS,
                                                RecordName.from(endpoint.legacyRegionalDnsName()),
                                                RecordData.fqdn(policy.canonicalName().get().value()),
                                                Priority.normal,
                                                ownerOf(allocation));
                    } else {
                        forwarder.removeRecords(Record.Type.DIRECT,
                                                RecordName.from(endpoint.dnsName()),
                                                RecordData.from(policy.ipAddress().get()),
                                                Priority.normal,
                                                ownerOf(allocation));
                        forwarder.removeRecords(Record.Type.DIRECT,
                                                RecordName.from(endpoint.legacyRegionalDnsName()),
                                                RecordData.from(policy.ipAddress().get()),
                                                Priority.normal,
                                                ownerOf(allocation));
                    }
                }
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

    /** Returns whether the endpoints of given policy are configured {@link RoutingStatus.Value#out} */
    private static boolean isConfiguredOut(ZoneRoutingPolicy zonePolicy, RoutingPolicy policy, Set<ZoneId> inactiveZones) {
        // A deployment can be configured out from endpoints at any of the following levels:
        // - zone level (ZoneRoutingPolicy)
        // - deployment level (RoutingPolicy)
        // - application package level (deployment.xml)
        return zonePolicy.routingStatus().value() == RoutingStatus.Value.out ||
               policy.routingStatus().value() == RoutingStatus.Value.out ||
               inactiveZones.contains(policy.id().zone());
    }

    /** Represents records for a region-wide endpoint */
    private static class RegionEndpoint {

        private final LatencyAliasTarget target;
        private final Set<WeightedAliasTarget> zoneAliasTargets = new LinkedHashSet<>();
        private final Set<WeightedDirectTarget> zoneDirectTargets = new LinkedHashSet<>();

        public RegionEndpoint(LatencyAliasTarget target) {
            this.target = Objects.requireNonNull(target);
        }

        public LatencyAliasTarget target() { return target; }
        public Set<AliasTarget> zoneAliasTargets() { return Collections.unmodifiableSet(zoneAliasTargets); }
        public Set<DirectTarget> zoneDirectTargets() { return Collections.unmodifiableSet(zoneDirectTargets); }

        public void add(WeightedAliasTarget target) { zoneAliasTargets.add(target); }
        public void add(WeightedDirectTarget target) { zoneDirectTargets.add(target); }

        public boolean active() {
            return zoneAliasTargets.stream().anyMatch(target -> target.weight() > 0) ||
                   zoneDirectTargets.stream().anyMatch(target -> target.weight() > 0);
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

    /** Active load balancers allocated to a deployment */
    record LoadBalancerAllocation(DeploymentId deployment,
                                  DeploymentSpec deploymentSpec,
                                  List<LoadBalancer> loadBalancers) {

        public LoadBalancerAllocation(DeploymentId deployment,
                                      DeploymentSpec deploymentSpec,
                                      List<LoadBalancer> loadBalancers) {
            this.deployment = deployment;
            this.loadBalancers = loadBalancers.stream().filter(LoadBalancerAllocation::isActive).toList();
            this.deploymentSpec = deploymentSpec;
        }

        private static boolean isActive(LoadBalancer loadBalancer) {
            return switch (loadBalancer.state()) {
                // Count reserved as active as we want to do DNS updates as early as possible
                case reserved, active -> true;
                default -> false;
            };
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
                // Legacy assignment always has the default endpoint ID
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
        return switch (controller.zoneRegistry().routingMethod(zone)) {
            case exclusive -> controller.nameServiceForwarder();
            case sharedLayer4 -> new NameServiceDiscarder(controller.curator());
        };
    }

    /** Denotes record data (record rhs) of either an ALIAS or a DIRECT target */
    private record Target(Record.Type type, RecordData data, DeploymentId deployment, Object aliasOrDirectTarget) {
        static Target weighted(RoutingPolicy policy, Endpoint.Target endpointTarget) {
            if (policy.ipAddress().isPresent()) {
                var wt = new WeightedDirectTarget(RecordData.from(policy.ipAddress().get()),
                        endpointTarget.deployment().zoneId(), endpointTarget.weight());
                return new Target(Record.Type.DIRECT, wt.recordData(), endpointTarget.deployment(), wt);
            }
            var wt = new WeightedAliasTarget(policy.canonicalName().get(), policy.dnsZone().get(),
                    endpointTarget.deployment().zoneId().value(), endpointTarget.weight());
            return new Target(Record.Type.ALIAS, RecordData.fqdn(wt.name().value()), endpointTarget.deployment(), wt);
        }
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

    private static Optional<TenantAndApplicationId> ownerOf(DeploymentId deploymentId) {
        return Optional.of(TenantAndApplicationId.from(deploymentId.applicationId()));
    }

    private static Optional<TenantAndApplicationId> ownerOf(LoadBalancerAllocation allocation) {
        return ownerOf(allocation.deployment);
    }

}

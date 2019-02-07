// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.RoutingPolicy;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Maintains DNS records as defined by routing policies for all exclusive load balancers in this system.
 *
 * @author mortent
 * @author mpolden
 */
public class RoutingPolicyMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(RoutingPolicyMaintainer.class.getName());

    private final NameService nameService;
    private final CuratorDb db;

    public RoutingPolicyMaintainer(Controller controller,
                                   Duration interval,
                                   JobControl jobControl,
                                   NameService nameService,
                                   CuratorDb db) {
        super(controller, interval, jobControl);
        this.nameService = nameService;
        this.db = db;
    }

    @Override
    protected void maintain() {
        Map<DeploymentId, List<LoadBalancer>> loadBalancers = loadBalancers(controller().applications().asList());
        updateDnsRecords(loadBalancers);
        removeObsoleteDnsRecords(loadBalancers);
    }

    /** Find all exclusive load balancers owned by given applications, grouped by deployment */
    private Map<DeploymentId, List<LoadBalancer>> loadBalancers(List<Application> applications) {
        Map<DeploymentId, List<LoadBalancer>> result = new LinkedHashMap<>();
        for (Application application : applications) {
            for (ZoneId zone : application.deployments().keySet()) {
                DeploymentId deployment = new DeploymentId(application.id(), zone);
                try {
                    List<LoadBalancer> loadBalancers = findLoadBalancersIn(deployment);
                    if (loadBalancers.isEmpty()) continue;
                    result.put(deployment, loadBalancers);
                } catch (Exception e) {
                    log.log(LogLevel.WARNING,
                            String.format("Got exception fetching load balancers for application: %s, in zone: %s. Retrying in %s",
                                          application.id().toShortString(), zone.value(), maintenanceInterval()),  e);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Create DNS records for all exclusive load balancers */
    private void updateDnsRecords(Map<DeploymentId, List<LoadBalancer>> loadBalancers) {
        for (Map.Entry<DeploymentId, List<LoadBalancer>> entry : loadBalancers.entrySet()) {
            ApplicationId application = entry.getKey().applicationId();
            ZoneId zone = entry.getKey().zoneId();
            try (Lock lock = db.lockRoutingPolicies()) {
                Set<RoutingPolicy> policies = new LinkedHashSet<>(db.readRoutingPolicies(application));
                for (LoadBalancer loadBalancer : entry.getValue()) {
                    try {
                        policies.add(registerDnsAlias(application, zone, loadBalancer));
                    } catch (Exception e) {
                        log.log(LogLevel.WARNING, "Failed to create or update DNS record for load balancer " +
                                                  loadBalancer.hostname() + ". Retrying in " + maintenanceInterval(),
                                e);
                    }
                }
                db.writeRoutingPolicies(application, policies);
            }
        }
    }

    /** Register DNS alias for given load balancer */
    private RoutingPolicy registerDnsAlias(ApplicationId application, ZoneId zone, LoadBalancer loadBalancer) {
        HostName alias = HostName.from(RoutingPolicy.createAlias(loadBalancer.cluster(), application, zone));
        RecordName name = RecordName.from(alias.value());
        RecordData data = RecordData.fqdn(loadBalancer.hostname().value());
        List<Record> existingRecords = nameService.findRecords(Record.Type.CNAME, name);
        if (existingRecords.size() > 1) {
            throw new IllegalStateException("Found more than 1 CNAME record for " + name.asString() + ": " + existingRecords);
        }
        Optional<Record> record = existingRecords.stream().findFirst();
        RecordId id;
        if (record.isPresent()) {
            id = record.get().id();
            nameService.updateRecord(id, data);
        } else {
            id = nameService.createCname(name, data);
        }
        return new RoutingPolicy(application, id.asString(), alias, loadBalancer.hostname(), loadBalancer.dnsZone(),
                                 loadBalancer.rotations());
    }

    /** Find all load balancers assigned to application in given zone */
    private List<LoadBalancer> findLoadBalancersIn(DeploymentId deployment) {
        try {
            return controller().applications().configServer().getLoadBalancers(deployment);
        } catch (Exception e) {
            log.log(LogLevel.WARNING,
                    String.format("Got exception fetching load balancers for application: %s, in zone: %s. Retrying in %s",
                                  deployment.applicationId().toShortString(), deployment.zoneId().value(),
                                  maintenanceInterval()),  e);
        }
        return Collections.emptyList();
    }

    /** Remove all DNS records that point to non-existing load balancers */
    private void removeObsoleteDnsRecords(Map<DeploymentId, List<LoadBalancer>> loadBalancers) {
        try (Lock lock = db.lockRoutingPolicies()) {
            List<RoutingPolicy> removalCandidates = new ArrayList<>(db.readRoutingPolicies());
            Set<HostName> activeLoadBalancers = loadBalancers.values().stream()
                                                             .flatMap(Collection::stream)
                                                             .map(LoadBalancer::hostname)
                                                             .collect(Collectors.toSet());

            // Remove any active load balancers
            removalCandidates.removeIf(lb -> activeLoadBalancers.contains(lb.canonicalName()));
            for (RoutingPolicy policy : removalCandidates) {
                try {
                    nameService.removeRecord(new RecordId(policy.recordId()));
                } catch (Exception e) {
                    log.log(LogLevel.WARNING, "Failed to remove DNS record with ID '" + policy.recordId() +
                                              "'. Retrying in " + maintenanceInterval());
                }
            }
        }
    }

}

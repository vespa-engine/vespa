// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.base.Strings;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.LockedApplication;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.loadbalancer.LoadBalancerName;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maintains loadbalancer endpoints.
 * Reads load balancer information for each application in all zones and updates name service.
 *
 * @author mortent
 */
public class LoadBalancerMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(LoadBalancerMaintainer.class.getName());
    private static final String IGNORE_ENDPOINT_VALUE = "default";

    private final NameService nameService;
    private final CuratorDb curatorDb;

    public LoadBalancerMaintainer(Controller controller,
                                  Duration interval,
                                  JobControl jobControl,
                                  NameService nameService,
                                  CuratorDb curatorDb) {
        super(controller, interval, jobControl);
        this.nameService = nameService;
        this.curatorDb = curatorDb;
    }

    @Override
    protected void maintain() {
        // update application object with load balancer information
        controller().applications().asList().forEach(this::updateApplicationLoadBalancers);

        // Create or update cnames
        List<Application> applications = controller().applications().asList();
        Map<ApplicationId, List<LoadBalancerName>> applicationEndpointMap = applications.stream()
                .collect(Collectors.toMap(Application::id, this::registerLoadBalancerEndpoint));

        try (Lock lock = curatorDb.lockLoadBalancerNames()) {
            updatePersistedLoadBalancerNames(applicationEndpointMap);
            removeObsoleteLoadBalancerNames();
        }
    }


    private void removeObsoleteLoadBalancerNames() {
        Map<ApplicationId, List<LoadBalancerName>> persistedLoadBalancerNames = new HashMap<>(curatorDb.readLoadBalancerNames());
        Map<ApplicationId, List<LoadBalancerName>> result = new HashMap<>();

        Map<ApplicationId, List<String>> wantedLoadBalancers = new HashMap<>();

        for (Application application : controller().applications().asList()) {
            for (Map.Entry<ZoneId, Deployment> entry : application.deployments().entrySet()) {
                Map<ClusterSpec.Id, HostName> loadBalancers = entry.getValue().loadBalancers();
                List<String> loadBalancerNames = loadBalancers.keySet().stream()
                        .map(cluster -> getEndpointName(cluster, application.id(), entry.getKey()))
                        .collect(Collectors.toList());
                wantedLoadBalancers.merge(application.id(), loadBalancerNames, (v1, v2) ->
                        Stream.concat(v1.stream(), v2.stream()).collect(Collectors.toList()));
            }
        }

        for (Map.Entry<ApplicationId, List<LoadBalancerName>> loadbalancerEntry : persistedLoadBalancerNames.entrySet()) {
            List<String> wanted = wantedLoadBalancers.getOrDefault(loadbalancerEntry.getKey(), Collections.emptyList());
            List<LoadBalancerName> current = loadbalancerEntry.getValue();
            List<LoadBalancerName> toBeRemoved = current.stream()
                    .filter(lbname -> !wanted.contains(lbname.recordName().asString()))
                    .collect(Collectors.toList());
            removeLoadBalancerNames(toBeRemoved);
            List<LoadBalancerName> resultingLoadBalancers = current.stream().filter(lb -> !toBeRemoved.contains(lb)).collect(Collectors.toList());
            result.put(loadbalancerEntry.getKey(), resultingLoadBalancers);
        }

        curatorDb.writeLoadBalancerNames(result);
    }

    private void updatePersistedLoadBalancerNames(Map<ApplicationId, List<LoadBalancerName>> applicationEndpointMap) {
        // Read current list of maintained load balancer endpoints
        Map<ApplicationId, List<LoadBalancerName>> existingApplicationEndpointList = curatorDb.readLoadBalancerNames();

        // Update ZK with new load balancer endpoints
        Map<ApplicationId, List<LoadBalancerName>> allCreated = new HashMap<>(applicationEndpointMap);
        existingApplicationEndpointList.forEach((k, v) -> allCreated.merge(k, v, (v1, v2) ->
                // Merge the two lists, removing duplicates
                List.copyOf(new LinkedHashSet<LoadBalancerName>(
                        Stream.concat(v1.stream(), v2.stream()).collect(Collectors.toList()))
                )));

        curatorDb.writeLoadBalancerNames(allCreated);

    }

    private void removeLoadBalancerNames(List<LoadBalancerName> loadBalancerNames) {
        if(loadBalancerNames.isEmpty()) return;
        log.log(LogLevel.INFO, String.format("Removing %d Load Balancer names", loadBalancerNames.size()));
        for (LoadBalancerName loadBalancerName : loadBalancerNames) {
            nameService.removeRecord(loadBalancerName.recordId());
        }
    }

    private void updateApplicationLoadBalancers(Application application) {
        // Get a list of all load balancers for this applications (for all zones and clusters)
        Map<ZoneId, List<LoadBalancer>> zoneLoadBalancers = new HashMap<>();
        for (ZoneId zoneId : application.deployments().keySet()) {
            try {
                zoneLoadBalancers.put(zoneId, controller().applications().configServer().getLoadBalancers(new DeploymentId(application.id(), zoneId)));
            } catch (Exception e) {
                log.log(LogLevel.WARNING,
                        String.format("Got exception fetching load balancers for application: %s, in zone: %s. Retrying in %s",
                                      application.id().toShortString(), zoneId.value(), maintenanceInterval()),
                        e);
            }
        }

        // store the load balancers on the deployments
        controller().applications().lockIfPresent(application.id(), lockedApplication -> storeApplicationWithLoadBalancers(lockedApplication, zoneLoadBalancers));
    }

    private void storeApplicationWithLoadBalancers(LockedApplication lockedApplication, Map<ZoneId, List<LoadBalancer>> loadBalancers) {
        for (Map.Entry<ZoneId, List<LoadBalancer>> entry : loadBalancers.entrySet()) {
            Map<ClusterSpec.Id, HostName> loadbalancerClusterMap = entry.getValue().stream()
                    .collect(Collectors.toMap(LoadBalancer::cluster, LoadBalancer::hostname));
            lockedApplication = lockedApplication.withDeploymentLoadBalancers(entry.getKey(), loadbalancerClusterMap);

        }
        controller().applications().store(lockedApplication);
    }

    private List<LoadBalancerName> registerLoadBalancerEndpoint(Application application) {
        List<LoadBalancerName> hostNamesRegistered = new ArrayList<>();
        for (Map.Entry<ZoneId, Deployment> deploymentEntry : application.deployments().entrySet()) {
            ZoneId zone = deploymentEntry.getKey();
            Deployment deployment = deploymentEntry.getValue();
            for (Map.Entry<ClusterSpec.Id, HostName> loadBalancers : deployment.loadBalancers().entrySet()) {
                try {
                    RecordName recordName = RecordName.from(getEndpointName(loadBalancers.getKey(), application.id(), zone));
                    RecordData recordData = RecordData.fqdn(loadBalancers.getValue().value());
                    Optional<Record> existingRecord = nameService.findRecord(Record.Type.CNAME, recordName);
                    final RecordId recordId;
                    if(existingRecord.isPresent()) {
                        recordId = existingRecord.get().id();
                        nameService.updateRecord(existingRecord.get().id(), recordData);
                    } else {
                        recordId = nameService.createCname(recordName, recordData);
                    }
                    hostNamesRegistered.add(new LoadBalancerName(recordId, recordName));
                } catch (Exception e) {
                    // Catching any exception, will be retried on next run
                    log.log(LogLevel.WARNING,
                            String.format("Got exception updating name service for application: %s, cluster: %s in zone: %s. Retrying in %s",
                                          application.id().toShortString(), loadBalancers.getKey().value(), zone.value(), maintenanceInterval()),
                            e);
                }
            }
        }
        return hostNamesRegistered;
    }

    static String getEndpointName(ClusterSpec.Id clusterId, ApplicationId applicationId, ZoneId zoneId) {
        List<String> endpointTerms = Arrays.asList(ignorePartIfDefault(clusterId.value()),
                                                   ignorePartIfDefault(applicationId.instance().value()),
                                                   applicationId.application().value(),
                                                   applicationId.tenant().value(),
                                                   zoneId.value(),
                                                   "vespa.oath.cloud"
        );
        return endpointTerms.stream()
                .filter(s -> !Strings.isNullOrEmpty((s)))
                .collect(Collectors.joining("."));
    }

    private static String ignorePartIfDefault(String s) {
        return IGNORE_ENDPOINT_VALUE.equalsIgnoreCase(s) ? "" : s;
    }
}

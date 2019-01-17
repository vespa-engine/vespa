// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.base.Strings;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.LockedApplication;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    public LoadBalancerMaintainer(Controller controller,
                                  Duration interval,
                                  JobControl jobControl,
                                  NameService nameService) {
        super(controller, interval, jobControl);
        this.nameService = nameService;
    }

    @Override
    protected void maintain() {
        // update application object with load balancer information
        controller().applications().asList().forEach(this::updateApplicationLoadBalancers);

        // Create or update cnames
        List<Application> applications = controller().applications().asList();
        applications.forEach(this::registerLoadBalancerEndpoint);

        // Delete removed application rotations
        // TODO
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

    private void registerLoadBalancerEndpoint(Application application) {
        for (Map.Entry<ZoneId, Deployment> deploymentEntry : application.deployments().entrySet()) {
            ZoneId zone = deploymentEntry.getKey();
            Deployment deployment = deploymentEntry.getValue();
            for (Map.Entry<ClusterSpec.Id, HostName> loadBalancers : deployment.loadBalancers().entrySet()) {
                try {
                    RecordName recordName = RecordName.from(getEndpointName(loadBalancers.getKey(), application.id(), zone));
                    RecordData recordData = RecordData.fqdn(loadBalancers.getValue().value());
                    Optional<Record> existingRecord = nameService.findRecord(Record.Type.CNAME, recordName);
                    if(existingRecord.isPresent()) {
                        nameService.updateRecord(existingRecord.get().id(), recordData);
                    } else {
                        nameService.createCname(recordName, recordData);
                    }
                } catch (Exception e) {
                    // Catching any exception, will be retried on next run
                    log.log(LogLevel.WARNING,
                            String.format("Got exception updating name service for application: %s, cluster: %s in zone: %s. Retrying in %s",
                                          application.id().toShortString(), loadBalancers.getKey().value(), zone.value(), maintenanceInterval()),
                            e);
                }
            }
        }
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

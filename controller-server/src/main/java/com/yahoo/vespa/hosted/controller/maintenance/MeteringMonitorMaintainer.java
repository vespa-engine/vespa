// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClient;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Reports discrepancies between currently deployed applications and
 * recently stored metering data in ResourceDatabaseClient.
 *
 * @author olaa
 */
public class MeteringMonitorMaintainer extends ControllerMaintainer {

    private final ResourceDatabaseClient resourceDatabaseClient;
    private final Metric metric;

    protected static final String STALE_METERING_METRIC_NAME = "metering.is_stale";
    private static final Logger logger = Logger.getLogger(MeteringMonitorMaintainer.class.getName());

    public MeteringMonitorMaintainer(Controller controller, Duration interval, ResourceDatabaseClient resourceDatabaseClient, Metric metric) {
        super(controller, interval, null, SystemName.allOf(SystemName::isPublic));
        this.resourceDatabaseClient = resourceDatabaseClient;
        this.metric = metric;
    }

    @Override
    protected double maintain() {
        var lastSnapshots = resourceDatabaseClient.getLastSnapshots();
        var activeDeployments = activeDeployments();
        var isStale = activeDeployments.entrySet()
                .stream()
                .anyMatch(entry -> {
                    var applicationId = entry.getKey();
                    var expectedZones = entry.getValue();
                    var actualZones = lastSnapshots.getOrDefault(applicationId, Set.of());
                    if (expectedZones.equals(actualZones))
                        return false;
                    logger.warning(
                            String.format("Metering discrepancy detected for application %s\n" +
                                    "Active deployments: %s\n" +
                                    "Last snapshots: %s\n" +
                                    "This message can be ignored if last snapshots contains a recently deleted deployment",
                                    applicationId.toFullString(), expectedZones, actualZones)
                    );
                    return true;
                });

        metric.set(STALE_METERING_METRIC_NAME, isStale ? 1 : 0, metric.createContext(Collections.emptyMap()));
        return 1;
    }


    private Map<ApplicationId, Set<ZoneId>> activeDeployments() {
        return controller().applications().asList()
                .stream()
                .flatMap(app -> app.instances().values().stream())
                .filter(instance -> instance.deployments().size() > 0)
                .collect(Collectors.toMap(
                        Instance::id,
                        instance -> instance.deployments().keySet()
                ));
    }
}

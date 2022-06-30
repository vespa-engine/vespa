// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClient;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reports discrepancies between currently deployed applications and
 * recently stored metering data in ResourceDatabaseClient.
 *
 * @author olaa
 */
public class MeteringMonitorMaintainer extends ControllerMaintainer {

    private final ResourceDatabaseClient resourceDatabaseClient;
    private final Metric metric;

    protected static final String METERING_AGE_METRIC_NAME = "metering.age.seconds";
    private static final Logger logger = Logger.getLogger(MeteringMonitorMaintainer.class.getName());

    public MeteringMonitorMaintainer(Controller controller, Duration interval, ResourceDatabaseClient resourceDatabaseClient, Metric metric) {
        super(controller, interval, null, SystemName.allOf(SystemName::isPublic));
        this.resourceDatabaseClient = resourceDatabaseClient;
        this.metric = metric;
    }

    @Override
    protected double maintain() {
        var activeDeployments = activeDeployments();
        var lastSnapshotTime = resourceDatabaseClient.getOldestSnapshotTimestamp(activeDeployments);
        var age = controller().clock().instant().getEpochSecond() - lastSnapshotTime.getEpochSecond();
        metric.set(METERING_AGE_METRIC_NAME, age, metric.createContext(Collections.emptyMap()));
        return 1;
    }

    private Set<DeploymentId> activeDeployments() {
        return controller().applications().asList()
                .stream()
                .flatMap(app -> app.instances().values().stream())
                .flatMap(this::toProdDeployments)
                .collect(Collectors.toSet());
    }

    private Stream<DeploymentId> toProdDeployments(Instance instance) {
        return instance.deployments()
                .keySet()
                .stream()
                .filter(deployment -> deployment.environment().isProduction())
                .map(deployment -> new DeploymentId(instance.id(), deployment));
    }
}

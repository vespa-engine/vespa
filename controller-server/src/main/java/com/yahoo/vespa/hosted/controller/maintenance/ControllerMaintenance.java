// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.Contacts;
import com.yahoo.vespa.hosted.controller.api.integration.Issues;
import com.yahoo.vespa.hosted.controller.api.integration.Properties;
import com.yahoo.vespa.hosted.controller.api.integration.chef.Chef;
import com.yahoo.vespa.hosted.controller.maintenance.config.MaintainerConfig;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;

/**
 * Maintenance jobs of the controller.
 * Each maintenance job is a singleton instance of its implementing class, created and owned by this,
 * and running its own dedicated thread.
 *
 * @author bratseth
 */
public class ControllerMaintenance extends AbstractComponent {

    private final JobControl jobControl;

    private final DeploymentExpirer deploymentExpirer;
    private final DeploymentIssueReporter deploymentIssueReporter;
    private final MetricsReporter metricsReporter;
    private final FailureRedeployer failureRedeployer;
    private final OutstandingChangeDeployer outstandingChangeDeployer;
    private final VersionStatusUpdater versionStatusUpdater;
    private final Upgrader upgrader;
    private final DelayedDeployer delayedDeployer;
    private final BlockedChangeDeployer blockedChangeDeployer;
    private final ClusterInfoMaintainer clusterInfoMaintainer;
    private final ClusterUtilizationMaintainer clusterUtilizationMaintainer;
    private final DeploymentMetricsMaintainer deploymentMetricsMaintainer;

    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ControllerMaintenance(MaintainerConfig maintainerConfig, Controller controller, CuratorDb curator,
                                 JobControl jobControl, Metric metric, Chef chefClient,
                                 Contacts contactsClient, Properties propertiesClient, Issues issuesClient) {
        Duration maintenanceInterval = Duration.ofMinutes(maintainerConfig.intervalMinutes());
        this.jobControl = jobControl;
        deploymentExpirer = new DeploymentExpirer(controller, maintenanceInterval, jobControl);
        deploymentIssueReporter = new DeploymentIssueReporter(controller, contactsClient, propertiesClient,
                                                              issuesClient,  maintenanceInterval, jobControl);
        metricsReporter = new MetricsReporter(controller, metric, chefClient, jobControl, controller.system());
        failureRedeployer = new FailureRedeployer(controller, maintenanceInterval, jobControl);
        outstandingChangeDeployer = new OutstandingChangeDeployer(controller, maintenanceInterval, jobControl);
        versionStatusUpdater = new VersionStatusUpdater(controller, Duration.ofMinutes(3), jobControl);
        upgrader = new Upgrader(controller, maintenanceInterval, jobControl, curator);
        delayedDeployer = new DelayedDeployer(controller, maintenanceInterval, jobControl);
        blockedChangeDeployer = new BlockedChangeDeployer(controller, maintenanceInterval, jobControl);
        clusterInfoMaintainer = new ClusterInfoMaintainer(controller, Duration.ofHours(2), jobControl);
        clusterUtilizationMaintainer = new ClusterUtilizationMaintainer(controller, Duration.ofHours(2), jobControl);
        deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(controller, Duration.ofMinutes(10), jobControl);
    }

    public Upgrader upgrader() { return upgrader; }
    
    /** Returns control of the maintenance jobs of this */
    public JobControl jobControl() { return jobControl; }

    @Override
    public void deconstruct() {
        deploymentExpirer.deconstruct();
        deploymentIssueReporter.deconstruct();
        metricsReporter.deconstruct();
        failureRedeployer.deconstruct();
        outstandingChangeDeployer.deconstruct();
        versionStatusUpdater.deconstruct();
        upgrader.deconstruct();
        delayedDeployer.deconstruct();
        blockedChangeDeployer.deconstruct();
        clusterUtilizationMaintainer.deconstruct();
        clusterInfoMaintainer.deconstruct();
        deploymentMetricsMaintainer.deconstruct();
    }

}

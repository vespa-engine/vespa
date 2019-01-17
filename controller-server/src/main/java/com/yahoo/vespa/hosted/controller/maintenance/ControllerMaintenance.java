// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ContactRetriever;
import com.yahoo.vespa.hosted.controller.authority.config.ApiAuthorityConfig;
import com.yahoo.vespa.hosted.controller.api.integration.chef.Chef;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.organization.DeploymentIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.maintenance.config.MaintainerConfig;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.restapi.cost.CostReportConsumer;
import com.yahoo.vespa.hosted.controller.restapi.cost.config.SelfHostedCostConfig;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    private final OutstandingChangeDeployer outstandingChangeDeployer;
    private final VersionStatusUpdater versionStatusUpdater;
    private final Upgrader upgrader;
    private final ReadyJobsTrigger readyJobsTrigger;
    private final ClusterInfoMaintainer clusterInfoMaintainer;
    private final ClusterUtilizationMaintainer clusterUtilizationMaintainer;
    private final DeploymentMetricsMaintainer deploymentMetricsMaintainer;
    private final ApplicationOwnershipConfirmer applicationOwnershipConfirmer;
    private final DnsMaintainer dnsMaintainer;
    private final SystemUpgrader systemUpgrader;
    private final List<OsUpgrader> osUpgraders;
    private final OsVersionStatusUpdater osVersionStatusUpdater;
    private final JobRunner jobRunner;
    private final ContactInformationMaintainer contactInformationMaintainer;
    private final CostReportMaintainer costReportMaintainer;
    private final LoadBalancerMaintainer loadbalancerMaintainer;

    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ControllerMaintenance(MaintainerConfig maintainerConfig, ApiAuthorityConfig apiAuthorityConfig, Controller controller, CuratorDb curator,
                                 JobControl jobControl, Metric metric, Chef chefClient,
                                 DeploymentIssues deploymentIssues, OwnershipIssues ownershipIssues,
                                 NameService nameService, NodeRepositoryClientInterface nodeRepositoryClient,
                                 ContactRetriever contactRetriever,
                                 CostReportConsumer reportConsumer,
                                 SelfHostedCostConfig selfHostedCostConfig) {
        Duration maintenanceInterval = Duration.ofMinutes(maintainerConfig.intervalMinutes());
        this.jobControl = jobControl;
        deploymentExpirer = new DeploymentExpirer(controller, maintenanceInterval, jobControl);
        deploymentIssueReporter = new DeploymentIssueReporter(controller, deploymentIssues, maintenanceInterval, jobControl);
        metricsReporter = new MetricsReporter(controller, metric, chefClient, jobControl, controller.system());
        outstandingChangeDeployer = new OutstandingChangeDeployer(controller, Duration.ofMinutes(1), jobControl);
        versionStatusUpdater = new VersionStatusUpdater(controller, Duration.ofMinutes(1), jobControl);
        upgrader = new Upgrader(controller, maintenanceInterval, jobControl, curator);
        readyJobsTrigger = new ReadyJobsTrigger(controller, Duration.ofMinutes(1), jobControl);
        clusterInfoMaintainer = new ClusterInfoMaintainer(controller, Duration.ofHours(2), jobControl, nodeRepositoryClient);
        clusterUtilizationMaintainer = new ClusterUtilizationMaintainer(controller, Duration.ofHours(2), jobControl);
        deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(controller, Duration.ofMinutes(5), jobControl);
        applicationOwnershipConfirmer = new ApplicationOwnershipConfirmer(controller, Duration.ofHours(12), jobControl, ownershipIssues);
        dnsMaintainer = new DnsMaintainer(controller, Duration.ofHours(12), jobControl, nameService);
        systemUpgrader = new SystemUpgrader(controller, Duration.ofMinutes(1), jobControl);
        jobRunner = new JobRunner(controller, Duration.ofMinutes(2), jobControl);
        osUpgraders = osUpgraders(controller, jobControl);
        osVersionStatusUpdater = new OsVersionStatusUpdater(controller, maintenanceInterval, jobControl);
        contactInformationMaintainer = new ContactInformationMaintainer(controller, Duration.ofHours(12), jobControl, contactRetriever);
        costReportMaintainer = new CostReportMaintainer(controller, Duration.ofHours(2), reportConsumer, jobControl, nodeRepositoryClient, Clock.systemUTC(), selfHostedCostConfig);
        loadbalancerMaintainer = new LoadBalancerMaintainer(controller, Duration.ofMinutes(5), jobControl, nameService);
    }

    public Upgrader upgrader() { return upgrader; }
    
    /** Returns control of the maintenance jobs of this */
    public JobControl jobControl() { return jobControl; }

    @Override
    public void deconstruct() {
        deploymentExpirer.deconstruct();
        deploymentIssueReporter.deconstruct();
        metricsReporter.deconstruct();
        outstandingChangeDeployer.deconstruct();
        versionStatusUpdater.deconstruct();
        upgrader.deconstruct();
        readyJobsTrigger.deconstruct();
        clusterUtilizationMaintainer.deconstruct();
        clusterInfoMaintainer.deconstruct();
        deploymentMetricsMaintainer.deconstruct();
        applicationOwnershipConfirmer.deconstruct();
        dnsMaintainer.deconstruct();
        systemUpgrader.deconstruct();
        osUpgraders.forEach(Maintainer::deconstruct);
        osVersionStatusUpdater.deconstruct();
        jobRunner.deconstruct();
        contactInformationMaintainer.deconstruct();
        costReportMaintainer.deconstruct();
        loadbalancerMaintainer.deconstruct();
    }

    /** Create one OS upgrader per cloud found in the zone registry of controller */
    private static List<OsUpgrader> osUpgraders(Controller controller, JobControl jobControl) {
        return controller.zoneRegistry().zones().controllerUpgraded().ids().stream()
                         .map(ZoneId::cloud)
                         .distinct()
                         .sorted()
                         .map(cloud -> new OsUpgrader(controller, Duration.ofMinutes(1), jobControl, cloud))
                         .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

}

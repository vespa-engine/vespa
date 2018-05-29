// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetireIPv4OnlyNodes;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicyList;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorSpareChecker;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorSpareCount;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A component which sets up all the node repo maintenance jobs.
 *
 * @author bratseth
 */
public class NodeRepositoryMaintenance extends AbstractComponent {

    private static final Logger log = Logger.getLogger(NodeRepositoryMaintenance.class.getName());
    private static final String envPrefix = "vespa_node_repository__";

    private final NodeFailer nodeFailer;
    private final PeriodicApplicationMaintainer periodicApplicationMaintainer;
    private final OperatorChangeApplicationMaintainer operatorChangeApplicationMaintainer;
    private final ReservationExpirer reservationExpirer;
    private final InactiveExpirer inactiveExpirer;
    private final RetiredExpirer retiredExpirer;
    private final FailedExpirer failedExpirer;
    private final DirtyExpirer dirtyExpirer;
    private final ProvisionedExpirer provisionedExpirer;
    private final NodeRebooter nodeRebooter;
    private final NodeRetirer nodeRetirer;
    private final MetricsReporter metricsReporter;
    private final InfrastructureProvisioner infrastructureProvisioner;

    private final JobControl jobControl;
    private final InfrastructureVersions infrastructureVersions;

    @Inject
    public NodeRepositoryMaintenance(NodeRepository nodeRepository, Deployer deployer, Provisioner provisioner,
                                     HostLivenessTracker hostLivenessTracker, ServiceMonitor serviceMonitor,
                                     Zone zone, Orchestrator orchestrator, Metric metric,
                                     ConfigserverConfig configserverConfig) {
        this(nodeRepository, deployer, provisioner, hostLivenessTracker, serviceMonitor, zone, Clock.systemUTC(),
                orchestrator, metric, configserverConfig);
    }

    public NodeRepositoryMaintenance(NodeRepository nodeRepository, Deployer deployer, Provisioner provisioner,
                                     HostLivenessTracker hostLivenessTracker, ServiceMonitor serviceMonitor,
                                     Zone zone, Clock clock, Orchestrator orchestrator, Metric metric,
                                     ConfigserverConfig configserverConfig) {
        DefaultTimes defaults = new DefaultTimes(zone);
        jobControl = new JobControl(nodeRepository.database());
        infrastructureVersions = new InfrastructureVersions(nodeRepository.database());

        nodeFailer = new NodeFailer(deployer, hostLivenessTracker, serviceMonitor, nodeRepository, durationFromEnv("fail_grace").orElse(defaults.failGrace), clock, orchestrator, throttlePolicyFromEnv("throttle_policy").orElse(defaults.throttlePolicy), metric, jobControl, configserverConfig);
        periodicApplicationMaintainer = new PeriodicApplicationMaintainer(deployer, nodeRepository, durationFromEnv("periodic_redeploy_interval").orElse(defaults.periodicRedeployInterval), jobControl);
        operatorChangeApplicationMaintainer = new OperatorChangeApplicationMaintainer(deployer, nodeRepository, clock, durationFromEnv("operator_change_redeploy_interval").orElse(defaults.operatorChangeRedeployInterval), jobControl);
        reservationExpirer = new ReservationExpirer(nodeRepository, clock, durationFromEnv("reservation_expiry").orElse(defaults.reservationExpiry), jobControl);
        retiredExpirer = new RetiredExpirer(nodeRepository, orchestrator, deployer, clock, durationFromEnv("retired_interval").orElse(defaults.retiredInterval), durationFromEnv("retired_expiry").orElse(defaults.retiredExpiry), jobControl);
        inactiveExpirer = new InactiveExpirer(nodeRepository, clock, durationFromEnv("inactive_expiry").orElse(defaults.inactiveExpiry), jobControl);
        failedExpirer = new FailedExpirer(nodeRepository, zone, clock, durationFromEnv("failed_expirer_interval").orElse(defaults.failedExpirerInterval), jobControl);
        dirtyExpirer = new DirtyExpirer(nodeRepository, clock, durationFromEnv("dirty_expiry").orElse(defaults.dirtyExpiry), jobControl);
        provisionedExpirer = new ProvisionedExpirer(nodeRepository, clock, durationFromEnv("provisioned_expiry").orElse(defaults.provisionedExpiry), jobControl);
        nodeRebooter = new NodeRebooter(nodeRepository, clock, durationFromEnv("reboot_interval").orElse(defaults.rebootInterval), jobControl);
        metricsReporter = new MetricsReporter(nodeRepository, metric, orchestrator, serviceMonitor, durationFromEnv("metrics_interval").orElse(defaults.metricsInterval), jobControl);
        infrastructureProvisioner = new InfrastructureProvisioner(provisioner, nodeRepository, infrastructureVersions, durationFromEnv("infrastructure_provision_interval").orElse(defaults.infrastructureProvisionInterval), jobControl);


        RetirementPolicy policy = new RetirementPolicyList(new RetireIPv4OnlyNodes(zone));
        FlavorSpareChecker flavorSpareChecker = new FlavorSpareChecker(
                NodeRetirer.SPARE_NODES_POLICY, FlavorSpareCount.constructFlavorSpareCountGraph(zone.nodeFlavors().get().getFlavors()));
        nodeRetirer = new NodeRetirer(nodeRepository, flavorSpareChecker, durationFromEnv("retire_interval").orElse(defaults.nodeRetirerInterval), deployer, jobControl, policy);
    }

    @Override
    public void deconstruct() {
        nodeFailer.deconstruct();
        periodicApplicationMaintainer.deconstruct();
        operatorChangeApplicationMaintainer.deconstruct();
        reservationExpirer.deconstruct();
        inactiveExpirer.deconstruct();
        retiredExpirer.deconstruct();
        failedExpirer.deconstruct();
        dirtyExpirer.deconstruct();
        nodeRebooter.deconstruct();
        nodeRetirer.deconstruct();
        provisionedExpirer.deconstruct();
        metricsReporter.deconstruct();
        infrastructureProvisioner.deconstruct();
    }

    public JobControl jobControl() { return jobControl; }

    public InfrastructureVersions infrastructureVersions() {
        return infrastructureVersions;
    }

    private static Optional<Duration> durationFromEnv(String envVariable) {
        return Optional.ofNullable(System.getenv(envPrefix + envVariable)).map(Long::parseLong).map(Duration::ofSeconds);
    }

    private static Optional<NodeFailer.ThrottlePolicy> throttlePolicyFromEnv(String envVariable) {
        String policyName = System.getenv(envPrefix + envVariable);
        try {
            return Optional.ofNullable(policyName).map(NodeFailer.ThrottlePolicy::valueOf);
        } catch (IllegalArgumentException e) {
            log.info(String.format("Ignoring invalid throttle policy name: '%s'. Must be one of %s", policyName,
                                   Arrays.toString(NodeFailer.ThrottlePolicy.values())));
            return Optional.empty();
        }
    }

    private static class DefaultTimes {

        /** All applications are redeployed with this period */
        private final Duration periodicRedeployInterval;
        /** Applications are redeployed after manual operator changes within this time period */
        private final Duration operatorChangeRedeployInterval;

        /** The time a node must be continuously nonresponsive before it is failed */
        private final Duration failGrace;
        
        private final Duration reservationExpiry;
        private final Duration inactiveExpiry;
        private final Duration retiredExpiry;
        private final Duration failedExpirerInterval;
        private final Duration dirtyExpiry;
        private final Duration provisionedExpiry;
        private final Duration rebootInterval;
        private final Duration nodeRetirerInterval;
        private final Duration metricsInterval;
        private final Duration retiredInterval;
        private final Duration infrastructureProvisionInterval;

        private final NodeFailer.ThrottlePolicy throttlePolicy;

        DefaultTimes(Zone zone) {
            failGrace = Duration.ofMinutes(60);
            periodicRedeployInterval = Duration.ofMinutes(30);
            operatorChangeRedeployInterval = Duration.ofMinutes(1);
            failedExpirerInterval = Duration.ofMinutes(10);
            provisionedExpiry = Duration.ofHours(4);
            reservationExpiry = Duration.ofMinutes(20); // Need to be long enough for deployment to be finished for all config model versions
            rebootInterval = Duration.ofDays(30);
            nodeRetirerInterval = Duration.ofMinutes(30);
            metricsInterval = Duration.ofMinutes(1);
            infrastructureProvisionInterval = Duration.ofMinutes(3);
            throttlePolicy = NodeFailer.ThrottlePolicy.hosted;

            Environment environment = zone.environment();
            if (environment.isTest())
                retiredExpiry = Duration.ofMinutes(1); // fast turnaround as test envs don't have persistent data
            else
                retiredExpiry = Duration.ofDays(4); // give up migrating data after 4 days


            if (environment.equals(Environment.prod) && zone.system() == SystemName.main) {
                inactiveExpiry = Duration.ofHours(4); // enough time for the application owner to discover and redeploy
                retiredInterval = Duration.ofMinutes(29);
                dirtyExpiry = Duration.ofHours(2); // enough time to clean the node
            } else {
                inactiveExpiry = Duration.ofSeconds(2); // support interactive wipe start over
                retiredInterval = Duration.ofMinutes(5);
                dirtyExpiry = Duration.ofMinutes(30);
            }
        }

    }

}

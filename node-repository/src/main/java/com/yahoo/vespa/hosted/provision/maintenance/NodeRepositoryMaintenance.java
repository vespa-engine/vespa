// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.hosted.provision.NodeRepository;
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
    private final ZooKeeperAccessMaintainer zooKeeperAccessMaintainer;
    private final ReservationExpirer reservationExpirer;
    private final InactiveExpirer inactiveExpirer;
    private final RetiredExpirer retiredExpirer;
    private final FailedExpirer failedExpirer;
    private final DirtyExpirer dirtyExpirer;
    private final NodeRebooter nodeRebooter;
    private final MetricsReporter metricsReporter;

    @Inject
    public NodeRepositoryMaintenance(NodeRepository nodeRepository, Deployer deployer, Curator curator,
                                     HostLivenessTracker hostLivenessTracker, ServiceMonitor serviceMonitor, 
                                     Zone zone, Orchestrator orchestrator, Metric metric) {
        this(nodeRepository, deployer, curator, hostLivenessTracker, serviceMonitor, zone, Clock.systemUTC(), orchestrator, metric);
    }

    public NodeRepositoryMaintenance(NodeRepository nodeRepository, Deployer deployer, Curator curator,
                                     HostLivenessTracker hostLivenessTracker, ServiceMonitor serviceMonitor, 
                                     Zone zone, Clock clock, Orchestrator orchestrator, Metric metric) {
        DefaultTimes defaults = new DefaultTimes(zone.environment());
        nodeFailer = new NodeFailer(deployer, hostLivenessTracker, serviceMonitor, nodeRepository, durationFromEnv("fail_grace").orElse(defaults.failGrace), clock, orchestrator, throttlePolicyFromEnv("throttle_policy").orElse(defaults.throttlePolicy));
        periodicApplicationMaintainer = new PeriodicApplicationMaintainer(deployer, nodeRepository, durationFromEnv("periodic_redeploy_interval").orElse(defaults.periodicRedeployInterval));
        operatorChangeApplicationMaintainer = new OperatorChangeApplicationMaintainer(deployer, nodeRepository, clock, durationFromEnv("operator_change_redeploy_interval").orElse(defaults.operatorChangeRedeployInterval));
        zooKeeperAccessMaintainer = new ZooKeeperAccessMaintainer(nodeRepository, curator, durationFromEnv("zookeeper_access_maintenance_interval").orElse(defaults.zooKeeperAccessMaintenanceInterval));
        reservationExpirer = new ReservationExpirer(nodeRepository, clock, durationFromEnv("reservation_expiry").orElse(defaults.reservationExpiry));
        retiredExpirer = new RetiredExpirer(nodeRepository, deployer, clock, durationFromEnv("retired_expiry").orElse(defaults.retiredExpiry));
        inactiveExpirer = new InactiveExpirer(nodeRepository, clock, durationFromEnv("inactive_expiry").orElse(defaults.inactiveExpiry));
        failedExpirer = new FailedExpirer(nodeRepository, zone, clock, durationFromEnv("failed_expiry").orElse(defaults.failedExpiry));
        dirtyExpirer = new DirtyExpirer(nodeRepository, clock, durationFromEnv("dirty_expiry").orElse(defaults.dirtyExpiry));
        nodeRebooter = new NodeRebooter(nodeRepository, clock, durationFromEnv("reboot_interval").orElse(defaults.rebootInterval));
        metricsReporter = new MetricsReporter(nodeRepository, metric, durationFromEnv("metrics_interval").orElse(defaults.metricsInterval));
    }

    @Override
    public void deconstruct() {
        nodeFailer.deconstruct();
        periodicApplicationMaintainer.deconstruct();
        operatorChangeApplicationMaintainer.deconstruct();
        zooKeeperAccessMaintainer.deconstruct();
        reservationExpirer.deconstruct();
        inactiveExpirer.deconstruct();
        retiredExpirer.deconstruct();
        failedExpirer.deconstruct();
        dirtyExpirer.deconstruct();
        nodeRebooter.deconstruct();
        metricsReporter.deconstruct();
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
        
        private final Duration zooKeeperAccessMaintenanceInterval;

        private final Duration reservationExpiry;
        private final Duration inactiveExpiry;
        private final Duration retiredExpiry;
        private final Duration failedExpiry;
        private final Duration dirtyExpiry;
        private final Duration rebootInterval;
        private final Duration metricsInterval;

        private final NodeFailer.ThrottlePolicy throttlePolicy;

        DefaultTimes(Environment environment) {
            if (environment.equals(Environment.prod)) {
                // These values are to avoid losing data (retired), and to be able to return an application
                // back to a previous state fast (inactive)
                failGrace = Duration.ofMinutes(60);
                periodicRedeployInterval = Duration.ofMinutes(30);
                operatorChangeRedeployInterval = Duration.ofMinutes(1);
                zooKeeperAccessMaintenanceInterval = Duration.ofMinutes(1);
                reservationExpiry = Duration.ofMinutes(20); // same as deployment timeout
                inactiveExpiry = Duration.ofHours(4); // enough time for the application owner to discover and redeploy
                retiredExpiry = Duration.ofDays(4); // enough time to migrate data
                failedExpiry = Duration.ofDays(4); // enough time to recover data even if it happens friday night
                dirtyExpiry = Duration.ofHours(2); // enough time to clean the node
                rebootInterval = Duration.ofDays(30);
                metricsInterval = Duration.ofMinutes(1);
                throttlePolicy = NodeFailer.ThrottlePolicy.hosted;
            } else {
                // These values ensure tests and development is not delayed due to nodes staying around
                // Use non-null values as these also determine the maintenance interval
                failGrace = Duration.ofMinutes(60);
                periodicRedeployInterval = Duration.ofMinutes(30);
                operatorChangeRedeployInterval = Duration.ofMinutes(1);
                zooKeeperAccessMaintenanceInterval = Duration.ofSeconds(10);
                reservationExpiry = Duration.ofMinutes(10); // Need to be long enough for deployment to be finished for all config model versions
                inactiveExpiry = Duration.ofSeconds(2); // support interactive wipe start over
                retiredExpiry = Duration.ofMinutes(1);
                failedExpiry = Duration.ofMinutes(10);
                dirtyExpiry = Duration.ofMinutes(30);
                rebootInterval = Duration.ofDays(30);
                metricsInterval = Duration.ofMinutes(1);
                throttlePolicy = NodeFailer.ThrottlePolicy.hosted;
            }
        }

    }

}

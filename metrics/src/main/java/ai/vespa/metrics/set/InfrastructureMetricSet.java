// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics.set;

import ai.vespa.metrics.ConfigServerMetrics;
import ai.vespa.metrics.ContainerMetrics;
import ai.vespa.metrics.ControllerMetrics;
import ai.vespa.metrics.LogdMetrics;
import ai.vespa.metrics.Suffix;
import ai.vespa.metrics.VespaMetrics;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static ai.vespa.metrics.Suffix.average;
import static ai.vespa.metrics.Suffix.count;
import static ai.vespa.metrics.Suffix.last;
import static ai.vespa.metrics.Suffix.max;
import static ai.vespa.metrics.Suffix.sum;

/**
 * Encapsulates vespa service metrics.
 *
 * @author yngveaasheim
 */
public class InfrastructureMetricSet {

    public static final MetricSet infrastructureMetricSet = new MetricSet("infrastructure",
            getInfrastructureMetrics());

    private static Set<Metric> getInfrastructureMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.addAll(getConfigServerMetrics());
        metrics.addAll(getControllerMetrics());
        metrics.addAll(getOtherMetrics());

        return Collections.unmodifiableSet(metrics);
    }

    private static Set<Metric> getConfigServerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ConfigServerMetrics.REQUESTS.count());
        addMetric(metrics, ConfigServerMetrics.FAILED_REQUESTS.count());
        addMetric(metrics, ConfigServerMetrics.LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, ConfigServerMetrics.CACHE_CONFIG_ELEMS.last());
        addMetric(metrics, ConfigServerMetrics.CACHE_CHECKSUM_ELEMS.last());
        addMetric(metrics, ConfigServerMetrics.HOSTS.last());
        addMetric(metrics, ConfigServerMetrics.DELAYED_RESPONSES.count());
        addMetric(metrics, ConfigServerMetrics.SESSION_CHANGE_ERRORS.count());

        addMetric(metrics, ConfigServerMetrics.ZK_Z_NODES, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last.
        addMetric(metrics, ConfigServerMetrics.ZK_AVG_LATENCY, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last.
        addMetric(metrics, ConfigServerMetrics.ZK_MAX_LATENCY, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last.
        addMetric(metrics, ConfigServerMetrics.ZK_CONNECTIONS, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last.
        addMetric(metrics, ConfigServerMetrics.ZK_CONNECTION_LOST.count());
        addMetric(metrics, ConfigServerMetrics.ZK_RECONNECTED.count());
        addMetric(metrics, ConfigServerMetrics.ZK_SUSPENDED.count());
        addMetric(metrics, ConfigServerMetrics.ZK_OUTSTANDING_REQUESTS, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last.

        // Node repository metrics
        addMetric(metrics, ConfigServerMetrics.NODES_NON_ACTIVE_FRACTION.last());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_COST.last());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_IDEAL_CPU.last());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_IDEAL_MEMORY.last());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_IDEAL_DISK.last());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_REBOOT.max());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_RESTART.max());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_RETIRE.max());
        addMetric(metrics, ConfigServerMetrics.RETIRED.max());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_CHANGE_VESPA_VERSION.max());
        addMetric(metrics, ConfigServerMetrics.HAS_WIRE_GUARD_KEY.last());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_DEPROVISION.max());
        addMetric(metrics, ConfigServerMetrics.SUSPENDED, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ConfigServerMetrics.SOME_SERVICES_DOWN.max());
        addMetric(metrics, ConfigServerMetrics.NODE_FAILER_BAD_NODE.last());
        addMetric(metrics, ConfigServerMetrics.LOCK_ATTEMPT_LOCKED_LOAD, EnumSet.of(max,average));

        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_CPU, EnumSet.of(average, last)); // TODO: Vespa 9: Remove last?
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_MEM, EnumSet.of(average, last)); // TODO: Vespa 9: Remove last?
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_DISK, EnumSet.of(average, last)); // TODO: Vespa 9: Remove last?
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_CPU.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_MEM.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_DISK.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_CPU, EnumSet.of(max,average));
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_DISK, EnumSet.of(max,average));
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_MEM, EnumSet.of(max,average));
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_SKEW.last());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_PENDING_REDEPLOYMENTS.last());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_ACTIVE_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DIRTY_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_FAILED_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_INACTIVE_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_PROVISIONED_HOSTS.last());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_READY_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_RESERVED_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_PARKED_HOSTS, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_ACTIVE_NODES.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_FAILED_NODES.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_PARKED_NODES, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last

        addMetric(metrics, ConfigServerMetrics.RPC_SERVER_WORK_QUEUE_SIZE.average());
        addMetric(metrics, ConfigServerMetrics.DEPLOYMENT_ACTIVATE_MILLIS.last());
        addMetric(metrics, ConfigServerMetrics.DEPLOYMENT_PREPARE_MILLIS.last());

        addMetric(metrics, ConfigServerMetrics.LOCK_ATTEMPT_LOCKED_LOAD, EnumSet.of(max, average));
        addMetric(metrics, ConfigServerMetrics.MAINTENANCE_SUCCESS_FACTOR_DEVIATION.last());
        addMetric(metrics, ConfigServerMetrics.MAINTENANCE_DEPLOYMENT_FAILURE.count());
        addMetric(metrics, ConfigServerMetrics.MAINTENANCE_DEPLOYMENT_TRANSIENT_FAILURE.count());
        addMetric(metrics, ConfigServerMetrics.OVERCOMMITTED_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.SPARE_HOST_CAPACITY.last());
        addMetric(metrics, ConfigServerMetrics.THROTTLED_NODE_FAILURES, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ConfigServerMetrics.NODE_FAIL_THROTTLING.last());

        addMetric(metrics, ConfigServerMetrics.ORCHESTRATOR_LOCK_ACQUIRE_SUCCESS.count());
        addMetric(metrics, ConfigServerMetrics.ORCHESTRATOR_LOCK_ACQUIRE_TIMEOUT.count());
        addMetric(metrics, ConfigServerMetrics.ZONE_WORKING.last());
        addMetric(metrics, ConfigServerMetrics.THROTTLED_HOST_PROVISIONING.max());

        // Container metrics that should be stored for the config-server
        addMetric(metrics, ContainerMetrics.HANDLED_LATENCY.max());
        addMetric(metrics, ContainerMetrics.HANDLED_REQUESTS.count());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_2XX.count());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_4XX.count());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_5XX.count());
        addMetric(metrics, ContainerMetrics.JDISC_GC_MS.last());
        addMetric(metrics, ContainerMetrics.MEM_HEAP_USED.average());
        addMetric(metrics, ContainerMetrics.SERVER_NUM_REQUESTS.count());
        addMetric(metrics, ContainerMetrics.SERVER_STARTED_MILLIS.last());
        addMetric(metrics, ContainerMetrics.SERVER_TOTAL_SUCCESSFUL_RESPONSE_LATENCY.last());

        return metrics;
    }

    private static Set<Metric> getControllerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ControllerMetrics.ATHENZ_REQUEST_ERROR.count());
        addMetric(metrics, ControllerMetrics.ARCHIVE_BUCKET_COUNT.last());
        addMetric(metrics, ControllerMetrics.BILLING_TENANTS.last());

        addMetric(metrics, ControllerMetrics.DEPLOYMENT_ABORT.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_AVERAGE_DURATION, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last.
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_CONVERGENCE_FAILURE.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_DEPLOYMENT_FAILURE.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_ERROR.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_FAILING_UPGRADES.last());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_FAILURE_PERCENTAGE.last());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_NODE_COUNT_BY_OS_VERSION.max());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_OS_CHANGE_DURATION.max());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_START.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_SUCCESS.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_TEST_FAILURE.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_WARNINGS.last());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_ENDPOINT_CERTIFICATE_TIMEOUT.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_BROKEN_SYSTEM_VERSION.last());

        addMetric(metrics, ControllerMetrics.OPERATION_APPLICATION.last());
        addMetric(metrics, ControllerMetrics.OPERATION_CHANGEMANAGEMENT.last());
        addMetric(metrics, ControllerMetrics.OPERATION_CONFIGSERVER.last());
        addMetric(metrics, ControllerMetrics.OPERATION_CONTROLLER.last());
        addMetric(metrics, ControllerMetrics.OPERATION_FLAGS.last());
        addMetric(metrics, ControllerMetrics.OPERATION_OS.last());
        addMetric(metrics, ControllerMetrics.OPERATION_ROUTING.last());
        addMetric(metrics, ControllerMetrics.OPERATION_ZONE.last());

        addMetric(metrics, ControllerMetrics.REMAINING_ROTATIONS.last());
        addMetric(metrics, ControllerMetrics.DNS_QUEUED_REQUESTS.last());
        addMetric(metrics, ControllerMetrics.ZMS_QUOTA_USAGE.last());
        addMetric(metrics, ControllerMetrics.COREDUMP_PROCESSED.count());

        addMetric(metrics, ControllerMetrics.METERING_AGE_SECONDS.last());

        return metrics;
    }

    private static Set<Metric> getOtherMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, LogdMetrics.LOGD_PROCESSED_LINES.count());

        return metrics;
    }

    private static void addMetric(Set<Metric> metrics, String nameWithSuffix) {
        metrics.add(new Metric(nameWithSuffix));
    }

    private static void addMetric(Set<Metric> metrics, VespaMetrics metric, EnumSet<Suffix> suffixes) {
        suffixes.forEach(suffix -> metrics.add(new Metric(metric.baseName() + "." + suffix.suffix())));
    }
}

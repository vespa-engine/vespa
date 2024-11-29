// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import static ai.vespa.metrics.Suffix.min;
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

        addMetric(metrics, ConfigServerMetrics.ZK_Z_NODES.max());
        addMetric(metrics, ConfigServerMetrics.ZK_MAX_LATENCY, EnumSet.of(max, average));
        addMetric(metrics, ConfigServerMetrics.ZK_CONNECTIONS.max());
        addMetric(metrics, ConfigServerMetrics.ZK_CONNECTION_LOST.count());
        addMetric(metrics, ConfigServerMetrics.ZK_RECONNECTED.count());
        addMetric(metrics, ConfigServerMetrics.ZK_SUSPENDED.count());
        addMetric(metrics, ConfigServerMetrics.ZK_OUTSTANDING_REQUESTS.max());

        // Node repository metrics
        addMetric(metrics, ConfigServerMetrics.NODES_ACTIVE.max());
        addMetric(metrics, ConfigServerMetrics.NODES_NON_ACTIVE_FRACTION.max());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_COST.max());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_IDEAL_CPU.max());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_IDEAL_MEMORY.max());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_IDEAL_DISK.max());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_PEAK_CPU.max());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_PEAK_MEMORY.max());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_PEAK_DISK.max());
        addMetric(metrics, ConfigServerMetrics.NODES_EMPTY_EXCLUSIVE.max());
        addMetric(metrics, ConfigServerMetrics.NODES_EXPIRED_DEPROVISIONED.count());
        addMetric(metrics, ConfigServerMetrics.NODES_EXPIRED_DIRTY.count());
        addMetric(metrics, ConfigServerMetrics.NODES_EXPIRED_INACTIVE.count());
        addMetric(metrics, ConfigServerMetrics.NODES_EXPIRED_PROVISIONED.count());
        addMetric(metrics, ConfigServerMetrics.NODES_EXPIRED_RESERVED.count());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_REBOOT.max());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_RESTART.max());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_RETIRE.max());
        addMetric(metrics, ConfigServerMetrics.RETIRED.max());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_CHANGE_VESPA_VERSION.max());
        addMetric(metrics, ConfigServerMetrics.HAS_WIRE_GUARD_KEY.max());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_DEPROVISION.max());
        addMetric(metrics, ConfigServerMetrics.SUSPENDED.max());
        addMetric(metrics, ConfigServerMetrics.SUSPENDED_SECONDS.count());
        addMetric(metrics, ConfigServerMetrics.ACTIVE_SECONDS.count());
        addMetric(metrics, ConfigServerMetrics.SOME_SERVICES_DOWN.max());
        addMetric(metrics, ConfigServerMetrics.NODE_FAILER_BAD_NODE.max());
        addMetric(metrics, ConfigServerMetrics.LOCK_ATTEMPT_LOCKED_LOAD, EnumSet.of(max,average));

        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_CPU.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_MEM.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_DISK.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_CPU.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_MEM.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_DISK.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_CPU.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_DISK.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_MEM.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_SKEW.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_PENDING_REDEPLOYMENTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_ACTIVE_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DIRTY_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_FAILED_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_INACTIVE_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_PROVISIONED_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_READY_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_RESERVED_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_PARKED_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_ACTIVE_NODES.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_FAILED_NODES.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_PARKED_NODES.max());

        addMetric(metrics, ConfigServerMetrics.RPC_SERVER_WORK_QUEUE_SIZE.average());
        addMetric(metrics, ConfigServerMetrics.DEPLOYMENT_ACTIVATE_MILLIS.max());
        addMetric(metrics, ConfigServerMetrics.DEPLOYMENT_PREPARE_MILLIS.max());

        addMetric(metrics, ConfigServerMetrics.LOCK_ATTEMPT_LOCKED_LOAD, EnumSet.of(max, average));
        addMetric(metrics, ConfigServerMetrics.MAINTENANCE_SUCCESS_FACTOR_DEVIATION.max());
        addMetric(metrics, ConfigServerMetrics.MAINTENANCE_DURATION.max());
        addMetric(metrics, ConfigServerMetrics.MAINTENANCE_DEPLOYMENT_FAILURE.count());
        addMetric(metrics, ConfigServerMetrics.MAINTENANCE_DEPLOYMENT_TRANSIENT_FAILURE.count());
        addMetric(metrics, ConfigServerMetrics.OVERCOMMITTED_HOSTS.max());
        addMetric(metrics, ConfigServerMetrics.SPARE_HOST_CAPACITY, EnumSet.of(min, max, last)); // TODO: Vespa 9: Remove last. WAIT
        addMetric(metrics, ConfigServerMetrics.THROTTLED_HOST_FAILURES.max());
        addMetric(metrics, ConfigServerMetrics.THROTTLED_NODE_FAILURES.max());
        addMetric(metrics, ConfigServerMetrics.NODE_FAIL_THROTTLING.max());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_AUTOSCALED.count());

        addMetric(metrics, ConfigServerMetrics.ORCHESTRATOR_LOCK_ACQUIRE_SUCCESS.count());
        addMetric(metrics, ConfigServerMetrics.ORCHESTRATOR_LOCK_ACQUIRE_TIMEOUT.count());
        addMetric(metrics, ConfigServerMetrics.ZONE_WORKING.max());
        addMetric(metrics, ConfigServerMetrics.THROTTLED_HOST_PROVISIONING.max());

        // Container metrics that should be stored for the config-server
        addMetric(metrics, ContainerMetrics.HANDLED_LATENCY.max());
        addMetric(metrics, ContainerMetrics.HANDLED_REQUESTS.count());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_2XX.count());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_4XX.count());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_5XX.count());
        addMetric(metrics, ContainerMetrics.JDISC_GC_MS.max());
        addMetric(metrics, ContainerMetrics.MEM_HEAP_USED.average());
        addMetric(metrics, ContainerMetrics.SERVER_NUM_REQUESTS.count());
        addMetric(metrics, ContainerMetrics.SERVER_STARTED_MILLIS.max());
        addMetric(metrics, ContainerMetrics.SERVER_TOTAL_SUCCESSFUL_RESPONSE_LATENCY.max());

        return metrics;
    }

    private static Set<Metric> getControllerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ControllerMetrics.ATHENZ_REQUEST_ERROR.count());
        addMetric(metrics, ControllerMetrics.ARCHIVE_BUCKET_COUNT.max());
        addMetric(metrics, ControllerMetrics.BILLING_TENANTS.max());

        addMetric(metrics, ControllerMetrics.DEPLOYMENT_JOBS_QUEUED, EnumSet.of(count, sum));
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_JOBS_ACTIVE, EnumSet.of(count, sum));
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_EXECUTOR_SIZE, EnumSet.of(max));
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_ABORT.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_DURATION.max());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_AVERAGE_DURATION.max());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_CONVERGENCE_FAILURE.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_NODE_ALLOCATION_FAILURE.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_DEPLOYMENT_FAILURE.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_ERROR.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_FAILING_UPGRADES.min());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_FAILURE_PERCENTAGE.max());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_NODE_COUNT_BY_OS_VERSION.max());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_OS_CHANGE_DURATION.max());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_START.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_SUCCESS.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_TEST_FAILURE.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_WARNINGS.max());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_ENDPOINT_CERTIFICATE_TIMEOUT.count());
        addMetric(metrics, ControllerMetrics.DEPLOYMENT_BROKEN_SYSTEM_VERSION.max());

        addMetric(metrics, ControllerMetrics.OPERATION_APPLICATION.max());
        addMetric(metrics, ControllerMetrics.OPERATION_CHANGEMANAGEMENT.max());
        addMetric(metrics, ControllerMetrics.OPERATION_CONFIGSERVER.max());
        addMetric(metrics, ControllerMetrics.OPERATION_CONTROLLER.max());
        addMetric(metrics, ControllerMetrics.OPERATION_FLAGS.max());
        addMetric(metrics, ControllerMetrics.OPERATION_OS.max());
        addMetric(metrics, ControllerMetrics.OPERATION_ROUTING.max());
        addMetric(metrics, ControllerMetrics.OPERATION_ZONE.max());

        addMetric(metrics, ControllerMetrics.REMAINING_ROTATIONS, EnumSet.of(min, max, last)); // TODO: Vespa 9: Remove last WAIT
        addMetric(metrics, ControllerMetrics.DNS_QUEUED_REQUESTS.max());
        addMetric(metrics, ControllerMetrics.ZMS_QUOTA_USAGE.max());
        addMetric(metrics, ControllerMetrics.COREDUMP_PROCESSED.count());
        addMetric(metrics, ControllerMetrics.AUTH0_EXCEPTIONS.count());
        addMetric(metrics, ControllerMetrics.BILLING_CREDITS.last());
        addMetric(metrics, ControllerMetrics.BILLING_EXCEPTIONS.count());
        addMetric(metrics, ControllerMetrics.BILLING_WEBHOOK_FAILURES.count());
        addMetric(metrics, ControllerMetrics.CERTIFICATE_POOL_AVAILABLE.max());
        addMetric(metrics, ControllerMetrics.CERTIFICATE_COUNT.max());
        addMetric(metrics, ControllerMetrics.CERTIFICATE_NAME_COUNT.max());

        addMetric(metrics, ControllerMetrics.METERING_AGE_SECONDS.min());
        addMetric(metrics, ControllerMetrics.METERING_LAST_REPORTED.max());

        addMetric(metrics, ControllerMetrics.MAIL_SENT.count());
        addMetric(metrics, ControllerMetrics.MAIL_FAILED.count());
        addMetric(metrics, ControllerMetrics.MAIL_THROTTLED.count());

        addMetric(metrics, ControllerMetrics.HUBSPOT_EXCEPTIONS.count());
        addMetric(metrics, ControllerMetrics.HUBSPOT_LAST_SUCCESS.last());

        addMetric(metrics, ControllerMetrics.TENANT_CREATED_LAST_SUCCESS.last());

        addMetric(metrics, ControllerMetrics.ATLASSIAN_EXCEPTIONS.count());
        addMetric(metrics, ControllerMetrics.ATLASSIAN_LAST_SUCCESS.last());

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

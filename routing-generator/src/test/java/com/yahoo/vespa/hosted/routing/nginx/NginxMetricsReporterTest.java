// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.nginx;

import com.google.common.jimfs.Jimfs;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.hosted.routing.RoutingTable;
import com.yahoo.vespa.hosted.routing.RoutingTable.Endpoint;
import com.yahoo.vespa.hosted.routing.RoutingTable.Target;
import com.yahoo.vespa.hosted.routing.mock.HealthStatusMock;
import com.yahoo.vespa.hosted.routing.status.ServerGroup;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author mortent
 * @author mpolden
 */
public class NginxMetricsReporterTest {

    private static final ApplicationId routingApp = ApplicationId.from("hosted-vespa", "routing", "default");

    private static final Target target0 = createTarget("vespa", "music", "prod", "gateway");
    private static final Target target1 = createTarget("vespa", "music", "prod", "qrs");
    private static final Target target2 = createTarget("vespa", "donbot", "default", "default");
    private static final Target target3 = createTarget("notchecked", "notchecked", "default", "default");
    private static final Target target4 = createTarget("not", "appearing-in-routing", "default", "default");
    private static final Target target5 = createTarget(routingApp.tenant().value(), routingApp.application().value(), routingApp.instance().value(), "routing");

    private final MockMetric metrics = new MockMetric();
    private final RoutingTable routingTable = createRoutingTable();
    private final HealthStatusMock healthService = new HealthStatusMock();
    private final FileSystem fileSystem = Jimfs.newFileSystem();
    private final NginxMetricsReporter reporter = new NginxMetricsReporter(routingApp, metrics, healthService,
                                                                           fileSystem, Duration.ofDays(1),
                                                                           () -> Optional.of(routingTable));

    @Test
    public void upstream_metrics() {
        List<ServerGroup.Server> servers = List.of(
                new ServerGroup.Server("gateway.prod.music.vespa.us-east-2.prod", "10.78.114.166:4080", true),
                new ServerGroup.Server("gateway.prod.music.vespa.us-east-2.prod", "10.78.115.68:4080", true),
                new ServerGroup.Server("qrs.prod.music.vespa.us-east-2.prod", "10.78.114.166:4080", true),
                new ServerGroup.Server("qrs.prod.music.vespa.us-east-2.prod", "10.78.115.68:4080", true),
                new ServerGroup.Server("qrs.prod.music.vespa.us-east-2.prod", "10.78.114.166:4080", false),
                new ServerGroup.Server("qrs.prod.music.vespa.us-east-2.prod", "10.78.115.68:4080", false),
                new ServerGroup.Server("qrs.prod.music.vespa.us-east-2.prod", "10.78.114.166:4080", false),
                new ServerGroup.Server("qrs.prod.music.vespa.us-east-2.prod", "10.78.115.68:4080", false),
                new ServerGroup.Server("donbot.vespa.us-east-2.prod", "10.201.8.47:4080", true),
                new ServerGroup.Server("donbot.vespa.us-east-2.prod", "10.201.14.46:4080", false),
                new ServerGroup.Server("appearing-in-routing.not.us-east-2.prod", "10.201.14.50:4080", false)
        );
        healthService.setStatus(new ServerGroup(servers));
        reporter.run();

        assertEquals(2D, getMetric(NginxMetricsReporter.UPSTREAM_UP_METRIC, dimensionsOf(target0)), Double.MIN_VALUE);
        assertEquals(0D, getMetric(NginxMetricsReporter.UPSTREAM_DOWN_METRIC, dimensionsOf(target0)), Double.MIN_VALUE);
        assertEquals(0D, getMetric(NginxMetricsReporter.UPSTREAM_UNKNOWN_METRIC, dimensionsOf(target0)), Double.MIN_VALUE);

        assertEquals(2L, getMetric(NginxMetricsReporter.UPSTREAM_UP_METRIC, dimensionsOf(target1)), Double.MIN_VALUE);
        assertEquals(4L, getMetric(NginxMetricsReporter.UPSTREAM_DOWN_METRIC, dimensionsOf(target1)), Double.MIN_VALUE);
        assertEquals(0L, getMetric(NginxMetricsReporter.UPSTREAM_UNKNOWN_METRIC, dimensionsOf(target1)), Double.MIN_VALUE);

        assertEquals(1D, getMetric(NginxMetricsReporter.UPSTREAM_UP_METRIC, dimensionsOf(target2)), Double.MIN_VALUE);
        assertEquals(1D, getMetric(NginxMetricsReporter.UPSTREAM_DOWN_METRIC, dimensionsOf(target2)), Double.MIN_VALUE);
        assertEquals(0D, getMetric(NginxMetricsReporter.UPSTREAM_UNKNOWN_METRIC, dimensionsOf(target2)), Double.MIN_VALUE);

        // If the application appears in routing table - but not in health check cache yet
        assertEquals(0D, getMetric(NginxMetricsReporter.UPSTREAM_UP_METRIC, dimensionsOf(target3)), Double.MIN_VALUE);
        assertEquals(0D, getMetric(NginxMetricsReporter.UPSTREAM_DOWN_METRIC, dimensionsOf(target3)), Double.MIN_VALUE);
        assertEquals(1D, getMetric(NginxMetricsReporter.UPSTREAM_UNKNOWN_METRIC, dimensionsOf(target3)), Double.MIN_VALUE);

        // If the application does not appear in routing table - but still appears in cache
        assertNull(getMetric(NginxMetricsReporter.UPSTREAM_UP_METRIC, dimensionsOf(target4)));
        assertNull(getMetric(NginxMetricsReporter.UPSTREAM_DOWN_METRIC, dimensionsOf(target4)));
        assertNull(getMetric(NginxMetricsReporter.UPSTREAM_UNKNOWN_METRIC, dimensionsOf(target4)));

        assertNull(getMetric(NginxMetricsReporter.UPSTREAM_UP_METRIC, dimensionsOf(target5)));
        assertNull(getMetric(NginxMetricsReporter.UPSTREAM_DOWN_METRIC, dimensionsOf(target5)));
        assertEquals(1D, getMetric(NginxMetricsReporter.UPSTREAM_UNKNOWN_METRIC, dimensionsOf(target5)), Double.MIN_VALUE);
    }

    @Test
    public void config_age_metric() throws Exception {
        reporter.run();
        // No files exist
        assertEquals(0D, getMetric(NginxMetricsReporter.CONFIG_AGE_METRIC), Double.MIN_VALUE);

        // Only temporary file exists
        Path configRoot = fileSystem.getPath("/opt/vespa/var/vespa-hosted/routing/");
        Path tempFile = configRoot.resolve("nginxl4.conf.tmp");
        createFile(tempFile, Instant.ofEpochSecond(123));
        reporter.run();
        assertEquals(123D, getMetric(NginxMetricsReporter.CONFIG_AGE_METRIC), Double.MIN_VALUE);

        // Only main file exists
        Files.delete(tempFile);
        createFile(configRoot.resolve("nginxl4.conf"), Instant.ofEpochSecond(456));
        reporter.run();
        assertEquals(0D, getMetric(NginxMetricsReporter.CONFIG_AGE_METRIC), Double.MIN_VALUE);

        // Both files exist
        createFile(tempFile, Instant.ofEpochSecond(123));
        reporter.run();
        assertEquals(333D, getMetric(NginxMetricsReporter.CONFIG_AGE_METRIC), Double.MIN_VALUE);
    }

    private double getMetric(String name) {
        return getMetric(name, Map.of());
    }

    private Double getMetric(String name, Map<String, ?> dimensions) {
        Map<Map<String, ?>, Double> metric = metrics.metrics().get(name);
        if (metric == null) throw new IllegalArgumentException("Metric '" + name + "' not found");
        return metric.get(dimensions);
    }

    private void createFile(Path path, Instant lastModified) throws IOException {
        Files.createDirectories(path.getParent());
        Files.createFile(path);
        Files.setLastModifiedTime(path, FileTime.from(lastModified));
    }

    private Map<String, ?> dimensionsOf(Target target) {
        return Map.of(
                "tenantName", target.tenant().value(),
                "app", String.format("%s.%s", target.application().value(), target.instance().get().value()),
                "applicationId", String.format("%s.%s.%s", target.tenant().value(), target.application().value(), target.instance().get().value()),
                "clusterid", target.cluster().value()
        );
    }

    private static Target createTarget(String tenantName, String applicationName, String instanceName, String clusterName) {
        ZoneId zone = ZoneId.from("prod", "us-east-2");
        ClusterSpec.Id cluster = ClusterSpec.Id.from(clusterName);
        return Target.create(ApplicationId.from(tenantName, applicationName, instanceName), cluster, zone, List.of());
    }

    private static RoutingTable createRoutingTable() {
        return new RoutingTable(Map.of(new Endpoint("endpoint0", RoutingMethod.sharedLayer4), target0,
                                       new Endpoint("endpoint1", RoutingMethod.sharedLayer4), target1,
                                       new Endpoint("endpoint2", RoutingMethod.sharedLayer4), target2,
                                       new Endpoint("endpoint3", RoutingMethod.sharedLayer4), target3),
                                42);
    }

}

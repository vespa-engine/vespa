// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistryMock;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClientMock;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceSnapshot;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.MetricsMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author olaa
 */
public class ResourceMeterMaintainerTest {

    private final ControllerTester tester = new ControllerTester(SystemName.Public);
    private final ResourceDatabaseClientMock resourceClient = new ResourceDatabaseClientMock(new PlanRegistryMock());
    private final MetricsMock metrics = new MetricsMock();
    private final ResourceMeterMaintainer maintainer =
            new ResourceMeterMaintainer(tester.controller(), Duration.ofMinutes(5), metrics, resourceClient);

    @Test
    void updates_deployment_costs() {
        ApplicationId app1 = ApplicationId.from("t1", "a1", "default");
        ApplicationId app2 = ApplicationId.from("t2", "a1", "default");
        ZoneId z1 = ZoneId.from("prod.aws-us-east-1c");
        ZoneId z2 = ZoneId.from("prod.aws-eu-west-1a");

        DeploymentTester deploymentTester = new DeploymentTester(tester);
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().region(z1.region()).region(z2.region()).trustDefaultCertificate().build();
        List.of(app1, app2).forEach(app -> deploymentTester.newDeploymentContext(app).submit(applicationPackage).deploy());

        BiConsumer<ApplicationId, Map<ZoneId, Double>> assertCost = (appId, costs) ->
                assertEquals(costs, tester.controller().applications().getInstance(appId).get().deployments().entrySet().stream()
                        .filter(entry -> entry.getValue().cost().isPresent())
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().cost().getAsDouble())));

        List<ResourceSnapshot> resourceSnapshots = List.of(
                new ResourceSnapshot(app1, 12, 34, 56, NodeResources.Architecture.getDefault(), Instant.EPOCH, z1),
                new ResourceSnapshot(app1, 23, 45, 67, NodeResources.Architecture.getDefault(), Instant.EPOCH, z2),
                new ResourceSnapshot(app2, 34, 56, 78, NodeResources.Architecture.getDefault(), Instant.EPOCH, z1));
        maintainer.updateDeploymentCost(resourceSnapshots);
        assertCost.accept(app1, Map.of(z1, 1.72, z2, 3.05));
        assertCost.accept(app2, Map.of(z1, 4.39));

        // Remove a region from app1 and add region to app2
        resourceSnapshots = List.of(
                new ResourceSnapshot(app1, 23, 45, 67, NodeResources.Architecture.getDefault(), Instant.EPOCH, z2),
                new ResourceSnapshot(app2, 34, 56, 78, NodeResources.Architecture.getDefault(), Instant.EPOCH, z1),
                new ResourceSnapshot(app2, 45, 67, 89, NodeResources.Architecture.getDefault(), Instant.EPOCH, z2));
        maintainer.updateDeploymentCost(resourceSnapshots);
        assertCost.accept(app1, Map.of(z2, 3.05));
        assertCost.accept(app2, Map.of(z1, 4.39, z2, 5.72));
        assertEquals(1.72,
                (Double) metrics.getMetric(context ->
                                z1.value().equals(context.get("zoneId")) &&
                                        app1.tenant().value().equals(context.get("tenant")),
                        "metering.cost.hourly").get(),
                Double.MIN_VALUE);
    }

    @Test
    void testMaintainer() {
        setUpZones();
        long lastRefreshTime = tester.clock().millis();
        tester.curator().writeMeteringRefreshTime(lastRefreshTime);
        maintainer.maintain();
        Collection<ResourceSnapshot> consumedResources = resourceClient.resourceSnapshots();

        // The mocked repository contains two applications, so we should also consume two ResourceSnapshots
        assertEquals(4, consumedResources.size());
        ResourceSnapshot app1 = consumedResources.stream().filter(snapshot -> snapshot.getApplicationId().equals(ApplicationId.from("tenant1", "app1", "default"))).findFirst().orElseThrow();
        ResourceSnapshot app2 = consumedResources.stream().filter(snapshot -> snapshot.getApplicationId().equals(ApplicationId.from("tenant2", "app2", "default"))).findFirst().orElseThrow();

        assertEquals(24, app1.getCpuCores(), Double.MIN_VALUE);
        assertEquals(24, app1.getMemoryGb(), Double.MIN_VALUE);
        assertEquals(500, app1.getDiskGb(), Double.MIN_VALUE);

        assertEquals(40, app2.getCpuCores(), Double.MIN_VALUE);
        assertEquals(24, app2.getMemoryGb(), Double.MIN_VALUE);
        assertEquals(500, app2.getDiskGb(), Double.MIN_VALUE);

        assertEquals(tester.clock().millis() / 1000, metrics.getMetric("metering_last_reported"));
        assertEquals(2224.0d, (Double) metrics.getMetric("metering_total_reported"), Double.MIN_VALUE);
        assertEquals(24d, (Double) metrics.getMetric(context -> "tenant1".equals(context.get("tenant")), "metering.vcpu").get(), Double.MIN_VALUE);
        assertEquals(40d, (Double) metrics.getMetric(context -> "tenant2".equals(context.get("tenant")), "metering.vcpu").get(), Double.MIN_VALUE);

        // Metering is not refreshed
        assertFalse(resourceClient.hasRefreshedMaterializedView());
        assertEquals(lastRefreshTime, tester.curator().readMeteringRefreshTime());

        var millisAdvanced = 3600 * 1000;
        tester.clock().advance(Duration.ofMillis(millisAdvanced));
        maintainer.maintain();
        assertTrue(resourceClient.hasRefreshedMaterializedView());
        assertEquals(lastRefreshTime + millisAdvanced, tester.curator().readMeteringRefreshTime());
    }

    private void setUpZones() {
        ZoneApiMock zone1 = ZoneApiMock.newBuilder().withId("prod.region-2").build();
        ZoneApiMock zone2 = ZoneApiMock.newBuilder().withId("test.region-3").build();
        tester.zoneRegistry().setZones(zone1, zone2);
        tester.configServer().nodeRepository().addFixedNodes(zone1.getId());
        tester.configServer().nodeRepository().addFixedNodes(zone2.getId());
        tester.configServer().nodeRepository().putNodes(zone1.getId(), createNodes());
    }

    private List<Node> createNodes() {
        return Stream.of(Node.State.provisioned,
                         Node.State.ready,
                         Node.State.dirty,
                         Node.State.failed,
                         Node.State.parked,
                         Node.State.active)
                     .map(state -> Node.builder()
                                       .hostname(HostName.of("host" + state))
                                       .parentHostname(HostName.of("parenthost" + state))
                                       .state(state)
                                       .type(NodeType.tenant)
                                       .owner(ApplicationId.from("tenant1", "app1", "default"))
                                       .currentVersion(Version.fromString("7.42"))
                                       .wantedVersion(Version.fromString("7.42"))
                                       .currentOsVersion(Version.fromString("7.6"))
                                       .wantedOsVersion(Version.fromString("7.6"))
                                       .serviceState(Node.ServiceState.expectedUp)
                                       .resources(new NodeResources(24, 24, 500, 1))
                                       .clusterId("clusterA")
                                       .clusterType(state == Node.State.active ? Node.ClusterType.admin : Node.ClusterType.container)
                                       .build())
                     .collect(Collectors.toUnmodifiableList());
    }
}

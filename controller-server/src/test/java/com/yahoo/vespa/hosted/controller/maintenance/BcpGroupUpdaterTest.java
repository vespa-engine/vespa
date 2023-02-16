// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Application;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Load;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the traffic fraction updater. This also tests its dependency on DeploymentMetricsMaintainer.
 *
 * @author bratseth
 */
public class BcpGroupUpdaterTest {

    @Test
    void testTrafficUpdaterImplicitBcp() {
        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(Version.fromString("7.1"));
        var context = tester.newDeploymentContext();
        var deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(tester.controller(), Duration.ofDays(1));
        var updater = new BcpGroupUpdater(tester.controller(), Duration.ofDays(1));
        ZoneId prod1 = ZoneId.from("prod", "ap-northeast-1");
        ZoneId prod2 = ZoneId.from("prod", "us-east-3");
        ZoneId prod3 = ZoneId.from("prod", "us-west-1");
        context.runJob(DeploymentContext.perfUsEast3, new ApplicationPackage(new byte[0]), version); // Ignored
        context.runJob(DeploymentContext.productionApNortheast1, new ApplicationPackage(new byte[0]), version);

        // One zone
        context.runJob(DeploymentContext.productionApNortheast1, new ApplicationPackage(new byte[0]), version);
        setQpsMetric(50.0, context.application().id().defaultInstance(), prod1, tester);
        setBcpMetrics(1.5, 0.1, 0.45, context.instanceId(), prod1, "cluster1", tester);
        deploymentMetricsMaintainer.maintain();
        assertEquals(1.0, updater.maintain(), 0.0000001);
        assertTrafficFraction(1.0, 1.0, context.instanceId(), prod1, tester);
        assertNoBcpGroupInfo(context.instanceId(), prod1, "cluster1", tester, "No other regions in group");

        // Two zones
        context.runJob(DeploymentContext.productionUsEast3, new ApplicationPackage(new byte[0]), version);
        setQpsMetric(60.0, context.application().id().defaultInstance(), prod1, tester);
        setQpsMetric(20.0, context.application().id().defaultInstance(), prod2, tester);
        setBcpMetrics(100.0, 0.1, 0.45, context.instanceId(), prod1, "cluster1", tester);
        deploymentMetricsMaintainer.maintain();
        assertEquals(1.0, updater.maintain(), 0.0000001);
        assertTrafficFraction(0.75, 1.0, context.instanceId(), prod1, tester);
        assertTrafficFraction(0.25, 1.0, context.instanceId(), prod2, tester);
        assertNoBcpGroupInfo(context.instanceId(), prod1, "cluster1", tester,
                             "Have no values from the other region (prod2) yet");
        assertBcpGroupInfo(100.0, 0.1, 0.45,
                           context.instanceId(), prod2, "cluster1", tester);
        setBcpMetrics(50.0, 0.2, 0.5, context.instanceId(), prod2, "cluster1", tester);
        assertEquals(1.0, updater.maintain(), 0.0000001);
        assertBcpGroupInfo(50.0, 0.2, 0.5,
                           context.instanceId(), prod1, "cluster1", tester);

        // Three zones
        context.runJob(DeploymentContext.productionUsWest1, new ApplicationPackage(new byte[0]), version);
        setQpsMetric(53.0, context.application().id().defaultInstance(), prod1, tester);
        setQpsMetric(45.0, context.application().id().defaultInstance(), prod2, tester);
        setQpsMetric(02.0, context.application().id().defaultInstance(), prod3, tester);
        deploymentMetricsMaintainer.maintain();
        assertEquals(1.0, updater.maintain(), 0.0000001);
        assertTrafficFraction(0.53, 0.53 + (double)45/2 / 100, context.instanceId(), prod1, tester);
        assertTrafficFraction(0.45, 0.45 + (double)53/2 / 100, context.instanceId(), prod2, tester);
        assertTrafficFraction(0.02, 0.02 + (double)53/2 / 100, context.instanceId(), prod3, tester);
    }

    @Test
    void testTrafficUpdaterHotCold() {
        var spec = """
                <deployment version="1.0">
                  <staging/>
                  <prod>
                    <region>ap-northeast-1</region>
                    <region>ap-southeast-1</region>
                    <region>us-east-3</region>
                    <region>us-central-1</region>
                    <region>eu-west-1</region>
                  </prod>
                  <bcp>
                    <group>
                      <region>ap-northeast-1</region>
                      <region>ap-southeast-1</region>
                    </group>
                    <group>
                      <region>us-east-3</region>
                      <region>us-central-1</region>
                    </group>
                    <group>
                      <region>eu-west-1</region>
                    </group>
                  </bcp>
                </deployment>
                """;

        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(Version.fromString("7.1"));
        var context = tester.newDeploymentContext();
        var deploymentSpec = new DeploymentSpecXmlReader(true).read(spec);
        tester.controller().applications()
              .lockApplicationOrThrow(context.application().id(),
                                      locked -> tester.controller().applications().store(locked.with(deploymentSpec)));

        var deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(tester.controller(), Duration.ofDays(1));
        var updater = new BcpGroupUpdater(tester.controller(), Duration.ofDays(1));

        ZoneId ap1 = ZoneId.from("prod", "ap-northeast-1");
        ZoneId ap2 = ZoneId.from("prod", "ap-southeast-1");
        ZoneId us1 = ZoneId.from("prod", "us-east-3");
        ZoneId us2 = ZoneId.from("prod", "us-central-1");
        ZoneId eu1 = ZoneId.from("prod", "eu-west-1");

        context.runJob(DeploymentContext.productionApNortheast1, new ApplicationPackage(new byte[0]), version);
        context.runJob(DeploymentContext.productionApSoutheast1, new ApplicationPackage(new byte[0]), version);
        context.runJob(DeploymentContext.productionUsEast3, new ApplicationPackage(new byte[0]), version);
        context.runJob(DeploymentContext.productionUsCentral1, new ApplicationPackage(new byte[0]), version);
        context.runJob(DeploymentContext.productionEuWest1, new ApplicationPackage(new byte[0]), version);

        setQpsMetric(50.0, context.application().id().defaultInstance(), ap1, tester);
        setQpsMetric(00.0, context.application().id().defaultInstance(), ap2, tester);
        setQpsMetric(10.0, context.application().id().defaultInstance(), us1, tester);
        setQpsMetric(00.0, context.application().id().defaultInstance(), us2, tester);
        setQpsMetric(40.0, context.application().id().defaultInstance(), eu1, tester);

        deploymentMetricsMaintainer.maintain();
        assertEquals(1.0, updater.maintain(), 0.0000001);
        assertTrafficFraction(0.5, 0.5, context.instanceId(), ap1, tester);
        assertTrafficFraction(0.0, 0.5, context.instanceId(), ap2, tester);
        assertTrafficFraction(0.1, 0.1, context.instanceId(), us1, tester);
        assertTrafficFraction(0.0, 0.1, context.instanceId(), us2, tester);
        assertTrafficFraction(0.4, 0.4, context.instanceId(), eu1, tester);
    }

    @Test
    void testTrafficUpdaterOverlappingGroups() {
        var spec = """
                <deployment version="1.0">
                  <staging/>
                  <prod>
                    <region>ap-northeast-1</region>
                    <region>ap-southeast-1</region>
                    <region>us-east-3</region>
                    <region>us-central-1</region>
                    <region>us-west-1</region>
                    <region>eu-west-1</region>
                  </prod>
                  <bcp>
                    <group>
                      <region>ap-northeast-1</region>
                      <region>ap-southeast-1</region>
                      <region fraction="0.5">eu-west-1</region>
                    </group>
                    <group>
                      <region>us-east-3</region>
                      <region>us-central-1</region>
                      <region>us-west-1</region>
                      <region fraction="0.5">eu-west-1</region>
                    </group>
                  </bcp>
                </deployment>
                """;

        DeploymentTester tester = new DeploymentTester();
        Version version = Version.fromString("7.1");
        tester.controllerTester().upgradeSystem(Version.fromString("7.1"));
        var context = tester.newDeploymentContext();
        var deploymentSpec = new DeploymentSpecXmlReader(true).read(spec);
        tester.controller().applications()
                  .lockApplicationOrThrow(context.application().id(),
                                          locked -> tester.controller().applications().store(locked.with(deploymentSpec)));

        var deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(tester.controller(), Duration.ofDays(1));
        var updater = new BcpGroupUpdater(tester.controller(), Duration.ofDays(1));

        ZoneId ap1 = ZoneId.from("prod", "ap-northeast-1");
        ZoneId ap2 = ZoneId.from("prod", "ap-southeast-1");
        ZoneId us1 = ZoneId.from("prod", "us-east-3");
        ZoneId us2 = ZoneId.from("prod", "us-central-1");
        ZoneId us3 = ZoneId.from("prod", "us-west-1");
        ZoneId eu1 = ZoneId.from("prod", "eu-west-1");

        context.runJob(DeploymentContext.productionApNortheast1, new ApplicationPackage(new byte[0]), version);
        context.runJob(DeploymentContext.productionApSoutheast1, new ApplicationPackage(new byte[0]), version);
        context.runJob(DeploymentContext.productionUsEast3, new ApplicationPackage(new byte[0]), version);
        context.runJob(DeploymentContext.productionUsCentral1, new ApplicationPackage(new byte[0]), version);
        context.runJob(DeploymentContext.productionUsWest1, new ApplicationPackage(new byte[0]), version);
        context.runJob(DeploymentContext.productionEuWest1, new ApplicationPackage(new byte[0]), version);

        setQpsMetric(20.0, context.application().id().defaultInstance(), ap1, tester);
        setQpsMetric(50.0, context.application().id().defaultInstance(), ap2, tester);
        setQpsMetric(00.0, context.application().id().defaultInstance(), us1, tester);
        setQpsMetric(30.0, context.application().id().defaultInstance(), us2, tester);
        setQpsMetric(40.0, context.application().id().defaultInstance(), us3, tester);
        setQpsMetric(60.0, context.application().id().defaultInstance(), eu1, tester);

        deploymentMetricsMaintainer.maintain();
        assertEquals(1.0, updater.maintain(), 0.0000001);
        assertTrafficFraction(0.10, 0.10 + 50 / 200.0 / 1.5, context.instanceId(), ap1, tester);
        assertTrafficFraction(0.25, 0.25 + 30 / 200.0 / 1.5, context.instanceId(), ap2, tester);
        assertTrafficFraction(0.00, 0.00 + 40 / 200.0 / 2.5, context.instanceId(), us1, tester);
        assertTrafficFraction(0.15, 0.15 + 40 / 200.0 / 2.5, context.instanceId(), us2, tester);
        assertTrafficFraction(0.20, 0.20 + 30 / 200.0 / 2.5, context.instanceId(), us3, tester);
        assertTrafficFraction(0.30, 0.30 + 0.5 * 50 / 200.0 / 1.5 + 0.5 * 40 / 200.0 / 2.5, context.instanceId(), eu1, tester);

        // BCP group info (missing ap* regions for cluster1, and full for cluster2)
        setBcpMetrics(100, 0.1, 0.1, context.instanceId(), us1, "cluster1", tester);
        setBcpMetrics(100, 0.1, 0.1, context.instanceId(), us2, "cluster1", tester);
        setBcpMetrics(300, 0.3, 0.3, context.instanceId(), us3, "cluster1", tester);
        setBcpMetrics(100, 0.1, 0.1, context.instanceId(), eu1, "cluster1", tester);

        setBcpMetrics(100, 0.1, 0.1, context.instanceId(), ap1, "cluster2", tester);
        setBcpMetrics(200, 0.2, 0.2, context.instanceId(), ap2, "cluster2", tester);
        setBcpMetrics(100, 0.1, 0.1, context.instanceId(), us1, "cluster2", tester);
        setBcpMetrics(100, 0.1, 0.1, context.instanceId(), us2, "cluster2", tester);
        setBcpMetrics(300, 0.3, 0.3, context.instanceId(), us3, "cluster2", tester);
        setBcpMetrics(100, 0.1, 0.1, context.instanceId(), eu1, "cluster2", tester);

        assertEquals(1.0, updater.maintain(), 0.0000001);

        assertNoBcpGroupInfo(context.instanceId(), ap1, "cluster1", tester, "No info in ap");
        assertNoBcpGroupInfo(context.instanceId(), ap2, "cluster1", tester, "No info in ap");
        assertBcpGroupInfo(300.0, 0.3, 0.3, context.instanceId(), us1, "cluster1", tester);
        assertBcpGroupInfo(300.0, 0.3, 0.3, context.instanceId(), us2, "cluster1", tester);
        assertBcpGroupInfo(100.0, 0.1, 0.1, context.instanceId(), us3, "cluster1", tester);
        assertBcpGroupInfo(300.0, 0.3, 0.3, context.instanceId(), eu1, "cluster1", tester);

        assertBcpGroupInfo(200.0, 0.2, 0.2, context.instanceId(), ap1, "cluster2", tester);
        assertBcpGroupInfo(100.0, 0.1, 0.1, context.instanceId(), ap2, "cluster2", tester);
        assertBcpGroupInfo(300.0, 0.3, 0.3, context.instanceId(), us1, "cluster2", tester);
        assertBcpGroupInfo(300.0, 0.3, 0.3, context.instanceId(), us2, "cluster2", tester);
        assertBcpGroupInfo(100.0, 0.1, 0.1, context.instanceId(), us3, "cluster2", tester);
        assertBcpGroupInfo((200 + 300) / 2.0, (0.2 + 0.3) / 2.0, (0.2 + 0.3) / 2.0, context.instanceId(), eu1, "cluster2", tester);
    }

    private void setQpsMetric(double qps, ApplicationId application, ZoneId zone, DeploymentTester tester) {
        var clusterMetrics = new ClusterMetrics("default", "container", Map.of(ClusterMetrics.QUERIES_PER_SECOND, qps));
        tester.controllerTester().serviceRegistry().configServerMock().setMetrics(new DeploymentId(application, zone), clusterMetrics);
    }

    private void assertTrafficFraction(double currentReadShare, double maxReadShare,
                                       ApplicationId application, ZoneId zone, DeploymentTester tester) {
        NodeRepositoryMock mock = (NodeRepositoryMock)tester.controller().serviceRegistry().configServer().nodeRepository();
        assertEquals(currentReadShare, mock.getTrafficFraction(application, zone).getFirst(), 0.00001, "Current read share");
        assertEquals(maxReadShare, mock.getTrafficFraction(application, zone).getSecond(), 0.00001, "Max read share");
    }

    private void setBcpMetrics(double queryRate, double growthRateHeadroom, double cpuCostPerQuery,
                               ApplicationId applicationId, ZoneId zone, String clusterId, DeploymentTester tester) {
        var application = tester.controller().applications().deploymentInfo().computeIfAbsent(new DeploymentId(applicationId, zone),
                                                                                              __ -> new Application(applicationId, List.of()));
        // ALl this is to pass Cluster.Autoscaling.Metrics - everything else is ignored
        var id = new ClusterSpec.Id(clusterId);
        var resources = new ClusterResources(10, 1, new NodeResources(10, 100, 1000, 0.1));
        var autoscaling = new Cluster.Autoscaling("ignored",
                                                  "ignored",
                                                  Optional.empty(),
                                                  Clock.systemUTC().instant(),
                                                  Load.zero(),
                                                  Load.zero(),
                                                  new Cluster.Autoscaling.Metrics(queryRate, growthRateHeadroom, cpuCostPerQuery));
        application.clusters().put(id, new Cluster(id,
                                                   ClusterSpec.Type.container,
                                                   resources,
                                                   resources,
                                                   IntRange.empty(),
                                                   resources,
                                                   autoscaling,
                                                   Cluster.Autoscaling.empty(),
                                                   List.of(),
                                                   Duration.ofHours(1)));
    }

    private void assertBcpGroupInfo(double queryRate, double growthRateHeadroom, double cpuCostPerQuery,
                                    ApplicationId application, ZoneId zone, String clusterId, DeploymentTester tester) {
        NodeRepositoryMock mock = (NodeRepositoryMock)tester.controller().serviceRegistry().configServer().nodeRepository();
        var info = mock.getBcpGroupInfo(application, zone, new ClusterSpec.Id(clusterId));
        assertNotNull(info, "Bcp group info of " + application + " cluster " + clusterId + " in " + zone);
        assertEquals(queryRate, info.queryRate(), 0.00001, "Query rate");
        assertEquals(growthRateHeadroom, info.growthRateHeadroom(), 0.00001, "Growth rate headroom");
        assertEquals(cpuCostPerQuery, info.cpuCostPerQuery(), 0.00001, "Cpu cost per query");
    }

    private void assertNoBcpGroupInfo(ApplicationId application, ZoneId zone, String clusterId, DeploymentTester tester, String explanation) {
        NodeRepositoryMock mock = (NodeRepositoryMock) tester.controller().serviceRegistry().configServer().nodeRepository();
        var info = mock.getBcpGroupInfo(application, zone, new ClusterSpec.Id(clusterId));
        assertNull(info, "No bcp group info of " + application + " cluster " + clusterId + " in " + zone + ": " + explanation);
    }

}

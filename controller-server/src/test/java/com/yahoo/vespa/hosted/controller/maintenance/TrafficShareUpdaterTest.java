// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.integration.NodeRepositoryMock;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the traffic fraction updater. This also tests its dependency on DeploymentMetricsMaintainer.
 *
 * @author bratseth
 */
public class TrafficShareUpdaterTest {

    @Test
    public void testTrafficUpdater() {
        DeploymentTester tester = new DeploymentTester();
        var application = tester.newDeploymentContext();
        var deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(tester.controller(), Duration.ofDays(1));
        var updater = new TrafficShareUpdater(tester.controller(), Duration.ofDays(1));
        ZoneId prod1 = ZoneId.from("prod", "ap-northeast-1");
        ZoneId prod2 = ZoneId.from("prod", "us-east-3");
        ZoneId prod3 = ZoneId.from("prod", "us-west-1");
        application.runJob(JobType.productionApNortheast1, new ApplicationPackage(new byte[0]), Version.fromString("7.1"));

        // Single zone
        setQpsMetric(50.0, application.application().id().defaultInstance(), prod1, tester);
        deploymentMetricsMaintainer.maintain();
        assertTrue(updater.maintain());
        assertTrafficFraction(1.0, 1.0, application.instanceId(), prod1, tester);

        // Two zones
        application.runJob(JobType.productionUsEast3, new ApplicationPackage(new byte[0]), Version.fromString("7.1"));
        // - one cold
        setQpsMetric(50.0, application.application().id().defaultInstance(), prod1, tester);
        setQpsMetric(0.0, application.application().id().defaultInstance(), prod2, tester);
        deploymentMetricsMaintainer.maintain();
        assertTrue(updater.maintain());
        assertTrafficFraction(1.0, 1.0, application.instanceId(), prod1, tester);
        assertTrafficFraction(0.0, 1.0, application.instanceId(), prod2, tester);
        // - both hot
        setQpsMetric(53.0, application.application().id().defaultInstance(), prod1, tester);
        setQpsMetric(47.0, application.application().id().defaultInstance(), prod2, tester);
        deploymentMetricsMaintainer.maintain();
        assertTrue(updater.maintain());
        assertTrafficFraction(0.53, 1.0, application.instanceId(), prod1, tester);
        assertTrafficFraction(0.47, 1.0, application.instanceId(), prod2, tester);

        // Three zones
        application.runJob(JobType.productionUsWest1, new ApplicationPackage(new byte[0]), Version.fromString("7.1"));
        // - one cold
        setQpsMetric(53.0, application.application().id().defaultInstance(), prod1, tester);
        setQpsMetric(47.0, application.application().id().defaultInstance(), prod2, tester);
        setQpsMetric(0.0, application.application().id().defaultInstance(), prod3, tester);
        deploymentMetricsMaintainer.maintain();
        assertTrue(updater.maintain());
        assertTrafficFraction(0.53, 0.53, application.instanceId(), prod1, tester);
        assertTrafficFraction(0.47, 0.50, application.instanceId(), prod2, tester);
        assertTrafficFraction(0.00, 0.50, application.instanceId(), prod3, tester);
        // - all hot
        setQpsMetric( 50.0, application.application().id().defaultInstance(), prod1, tester);
        setQpsMetric(25.0, application.application().id().defaultInstance(), prod2, tester);
        setQpsMetric(25.0, application.application().id().defaultInstance(), prod3, tester);
        deploymentMetricsMaintainer.maintain();
        assertTrue(updater.maintain());
        assertTrafficFraction(0.50, 0.5, application.instanceId(), prod1, tester);
        assertTrafficFraction(0.25, 0.5, application.instanceId(), prod2, tester);
        assertTrafficFraction(0.25, 0.5, application.instanceId(), prod3, tester);
    }

    private void setQpsMetric(double qps, ApplicationId application, ZoneId zone, DeploymentTester tester) {
        var clusterMetrics = new ClusterMetrics("default", "container");
        clusterMetrics = clusterMetrics.addMetric(ClusterMetrics.QUERIES_PER_SECOND, qps);
        tester.controllerTester().serviceRegistry().configServerMock().setMetrics(new DeploymentId(application, zone), clusterMetrics);
    }

    private void assertTrafficFraction(double currentReadShare, double maxReadShare,
                                       ApplicationId application, ZoneId zone, DeploymentTester tester) {
        NodeRepositoryMock mock = (NodeRepositoryMock)tester.controller().serviceRegistry().configServer().nodeRepository();
        assertEquals(currentReadShare, mock.getTrafficFraction(application, zone).getFirst(), 0.00001);
        assertEquals(maxReadShare, mock.getTrafficFraction(application, zone).getSecond(), 0.00001);
    }

}

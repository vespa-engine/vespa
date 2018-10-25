// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.metrics;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.RotationStatus;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Before;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author olaa
 */
public class MetricForwardingApiHandlerTest extends ControllerContainerTest {

    private static final double DELTA = 0.00001;
    private ContainerControllerTester tester;

    @Before
    public void before() {
        tester = new ContainerControllerTester(container, null);
    }

    @Test
    public void testUpdatingDeploymentMetrics() {
        ZoneId zoneId = ZoneId.from(Environment.test, RegionName.from("us-east-1"));
        deployApplication(zoneId);
        Application application = getUpdatedApplication();
        Deployment deployment = application.deployments().get(zoneId);

        // Verify deployment and system metrics are initially 0
        assertEquals(0, application.metrics().queryServiceQuality(), DELTA);
        assertEquals(0, application.metrics().writeServiceQuality(), DELTA);
        assertEquals(0, deployment.metrics().documentCount(), DELTA);
        assertEquals(0, deployment.metrics().queriesPerSecond(), DELTA);
        assertEquals(0, deployment.metrics().queryLatencyMillis(), DELTA);
        assertEquals(0, deployment.metrics().writeLatencyMillis(), DELTA);
        assertEquals(0, deployment.metrics().writesPerSecond(), DELTA);
        assertFalse(application.rotationStatus().containsKey(HostName.from("host1")));
        assertFalse(application.rotationStatus().containsKey(HostName.from("host2")));

        String deploymentMetrics = "[{\"applicationId\":\"tenant1:application1:default\"," +
                "\"applicationMetrics\":{" +
                "   \"queryServiceQuality\":0.5," +
                "   \"writeServiceQuality\":0.7}," +
                "\"rotationStatus\":[" +
                "   {" +
                "       \"hostname\":\"proxy.prod.us-east-3.vip.test\"," +
                "       \"rotationStatus\":\"out\"" +
                "   }," +
                "   {" +
                "       \"hostname\":\"proxy.prod.us-east-1.vip.test\"," +
                "       \"rotationStatus\":\"in\"" +
                "   }]," +
                "\"deploymentMetrics\":[" +
                "   {" +
                "       \"zoneId\":\"test.us-east-1\"," +
                "       \"queriesPerSecond\":1.0," +
                "       \"writesPerSecond\":2.0," +
                "       \"documentCount\":3.0," +
                "       \"queryLatencyMillis\":4.0," +
                "       \"writeLatencyMillis\":5.0" +
                "   }" +
                "]}]";
        String expectedResponseMessage = "Added deployment metrics";
        assertResponse(new Request("http://localhost:8080/metricforwarding/v1/deploymentmetrics", deploymentMetrics, Request.Method.POST), 200, expectedResponseMessage);

        // Verify that deployment metrics are updated
        application = getUpdatedApplication();
        assertEquals(0.5, application.metrics().queryServiceQuality(), DELTA);
        assertEquals(0.7, application.metrics().writeServiceQuality(), DELTA);

        deployment = application.deployments().get(zoneId);
        assertEquals(3.0, deployment.metrics().documentCount(), DELTA);
        assertEquals(1.0, deployment.metrics().queriesPerSecond(), DELTA);
        assertEquals(4.0, deployment.metrics().queryLatencyMillis(), DELTA);
        assertEquals(5.0, deployment.metrics().writeLatencyMillis(), DELTA);
        assertEquals(2, deployment.metrics().writesPerSecond(), DELTA);
        assertEquals(RotationStatus.in, application.rotationStatus().get(HostName.from("proxy.prod.us-east-1.vip.test")));
        assertEquals(RotationStatus.out, application.rotationStatus().get(HostName.from("proxy.prod.us-east-3.vip.test")));

    }

    @Test
    public void testUpdatingSystemMetrics() {
        ZoneId zoneId = ZoneId.from(Environment.test, RegionName.from("us-east-1"));
        deployApplication(zoneId);
        Application application = getUpdatedApplication();
        Deployment deployment = application.deployments().get(zoneId);
        assertFalse(deployment.clusterUtils().containsKey(ClusterSpec.Id.from("cluster1")));
        assertFalse(deployment.clusterUtils().containsKey(ClusterSpec.Id.from("cluster2")));
        String systemMetrics = "[{\"applicationId\":\"tenant1:application1:default\"," +
                "\"deployments\":[{\"zoneId\":\"test.us-east-1\",\"clusterUtil\":[" +
                "{" +
                "   \"clusterSpecId\":\"default\"," +
                "   \"cpu\":0.5554," +
                "   \"memory\":0.6990000000000001," +
                "   \"disk\":0.34590000000000004," +
                "   \"diskBusy\":0.0" +
                "}," +
                "{" +
                "   \"clusterSpecId\":\"cluster2\"," +
                "   \"cpu\":0.6," +
                "   \"memory\":0.8," +
                "   \"disk\":0.5," +
                "   \"diskBusy\":0.1" +
                "}" +
                "]}]}]";
        String expectedResponseMessage = "Added cluster utilization metrics";
        assertResponse(new Request("http://localhost:8080/metricforwarding/v1/clusterutilization", systemMetrics, Request.Method.POST), 200, expectedResponseMessage);
        deployment = getUpdatedApplication().deployments().get(zoneId);

        ClusterUtilization clusterUtilization = deployment.clusterUtils().get(ClusterSpec.Id.from("default"));
        assertEquals(0.5554, clusterUtilization.getCpu(), DELTA);
        assertEquals(0.699, clusterUtilization.getMemory(), DELTA);
        assertEquals(0.3459, clusterUtilization.getDisk(), DELTA);
        assertEquals(0.0, clusterUtilization.getDiskBusy(), DELTA);
        clusterUtilization = deployment.clusterUtils().get(ClusterSpec.Id.from("cluster2"));
        assertEquals(0.6, clusterUtilization.getCpu(), DELTA);
        assertEquals(0.8, clusterUtilization.getMemory(), DELTA);
        assertEquals(0.5, clusterUtilization.getDisk(), DELTA);
        assertEquals(0.1, clusterUtilization.getDiskBusy(), DELTA);
    }

    private Application getUpdatedApplication() {
        return tester.controller().applications().asList().get(0);
    }

    private void deployApplication(ZoneId zoneId) {
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(zoneId.environment())
                .region(zoneId.region().value())
                .build();
        Application application = tester.createApplication();
        tester.jobCompletion(JobType.component)
                .application(application)
                .projectId(123L)
                .uploadArtifact(applicationPackage)
                .submit();
        tester.deploy(application, applicationPackage, zoneId);
    }
}
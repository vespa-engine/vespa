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


        String deploymentMetrics = "{" +
                "   \"applicationMetrics\":{" +
                "        \"queryServiceQuality\":100.0," +
                "        \"writeServiceQuality\":95.0" +
                "    }," +
                "   \"rotationStatus\": [{" +
                "       \"hostname\":\"host1\"," +
                "       \"rotationStatus\":\"in\"" +
                "   }, " +
                "   {" +
                "       \"hostname\":\"host2\"," +
                "       \"rotationStatus\":\"out\"" +
                "   }]," +
                "   \"deploymentMetrics\":[{" +
                "        \"zoneId\": \"" + zoneId.value() + "\"," +
                "        \"queriesPerSecond\":10000.0," +
                "        \"writesPerSecond\":123.0," +
                "        \"documentCount\":10000.0," +
                "        \"queryLatencyMillis\":123.0," +
                "        \"writeLatencyMillis\":123.0" +
                "   }]" +
                "}";
        String expectedResponseMessage = "Added deployment metrics for tenant1.application1";
        assertResponse(new Request("http://localhost:8080/metricforwarding/v1/tenant/tenant1/application/application1/instance/default/deploymentmetrics", deploymentMetrics, Request.Method.POST), 200, expectedResponseMessage);

        // Verify that deployment metrics are updated
        application = getUpdatedApplication();
        deployment = application.deployments().get(zoneId);
        assertEquals(100.0, application.metrics().queryServiceQuality(), DELTA);
        assertEquals(95.0, application.metrics().writeServiceQuality(), DELTA);
        assertEquals(10000.0, deployment.metrics().documentCount(), DELTA);
        assertEquals(10000.0, deployment.metrics().queriesPerSecond(), DELTA);
        assertEquals(123.0, deployment.metrics().queryLatencyMillis(), DELTA);
        assertEquals(123.0, deployment.metrics().writeLatencyMillis(), DELTA);
        assertEquals(123.0, deployment.metrics().writesPerSecond(), DELTA);
        assertEquals(RotationStatus.in, application.rotationStatus().get(HostName.from("host1")));
        assertEquals(RotationStatus.out, application.rotationStatus().get(HostName.from("host2")));

    }

    @Test
    public void testUpdatingSystemMetrics() {
        ZoneId zoneId = ZoneId.from(Environment.test, RegionName.from("us-east-1"));
        deployApplication(zoneId);
        Application application = getUpdatedApplication();
        Deployment deployment = application.deployments().get(zoneId);
        assertFalse(deployment.clusterUtils().containsKey(ClusterSpec.Id.from("cluster1")));
        assertFalse(deployment.clusterUtils().containsKey(ClusterSpec.Id.from("cluster2")));
        String systemMetrics = "[" +
                "    {" +
                "        \"clusterSpecId\":\"cluster1\"," +
                "        \"cpu\":100.0," +
                "        \"memory\":90.0," +
                "        \"disk\":80.0," +
                "        \"diskbusy\":70.0" +
                "    }," +
                "    {" +
                "        \"clusterSpecId\":\"cluster2\"," +
                "        \"cpu\":70.0," +
                "        \"memory\":80.0," +
                "        \"disk\":90.0," +
                "        \"diskbusy\":100.0" +
                "    }" +
                "]";
        String expectedResponseMessage = "Added cluster utilization metrics for tenant1.application1";
        assertResponse(new Request("http://localhost:8080/metricforwarding/v1/tenant/tenant1/application/application1/environment/test/region/us-east-1/instance/default/clusterutilization", systemMetrics, Request.Method.POST), 200, expectedResponseMessage);
        deployment = getUpdatedApplication().deployments().get(zoneId);
        ClusterUtilization clusterUtilization = deployment.clusterUtils().get(ClusterSpec.Id.from("cluster1"));
        assertEquals(100.0, clusterUtilization.getCpu(), DELTA);
        assertEquals(90.0, clusterUtilization.getMemory(), DELTA);
        assertEquals(80.0, clusterUtilization.getDisk(), DELTA);
        assertEquals(70.0, clusterUtilization.getDiskBusy(), DELTA);
        clusterUtilization = deployment.clusterUtils().get(ClusterSpec.Id.from("cluster2"));
        assertEquals(70.0, clusterUtilization.getCpu(), DELTA);
        assertEquals(80.0, clusterUtilization.getMemory(), DELTA);
        assertEquals(90.0, clusterUtilization.getDisk(), DELTA);
        assertEquals(100.0, clusterUtilization.getDiskBusy(), DELTA);
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
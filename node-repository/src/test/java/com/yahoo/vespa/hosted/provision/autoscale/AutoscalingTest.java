// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class AutoscalingTest {

    @Test
    public void testAutoscaling() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, resources);

        assertTrue("No measurements -> No change", tester.autoscale(application1, cluster1).isEmpty());

        tester.addMeasurements( 0.25f, 60, application1);
        assertTrue("Too few measurements -> No change", tester.autoscale(application1, cluster1).isEmpty());

        tester.addMeasurements( 0.25f, 60, application1);
        ClusterResources scaledResources = tester.assertResources("Scaling up since resource usage is too high",
                                                                 10, 1.7,  44.4, 44.4,
                                                                  tester.autoscale(application1, cluster1));

        tester.deploy(application1, cluster1, scaledResources);
        assertTrue("Cluster in flux -> No further change", tester.autoscale(application1, cluster1).isEmpty());

        tester.deactivateRetired(application1, cluster1, scaledResources);
        tester.addMeasurements( 0.8f, 3, application1);
        assertTrue("Load change is large, but insufficient measurements for new config -> No change",
                   tester.autoscale(application1, cluster1).isEmpty());

        tester.addMeasurements( 0.19f, 100, application1);
        assertTrue("Load change is small -> No change", tester.autoscale(application1, cluster1).isEmpty());

        tester.addMeasurements( 0.1f, 120, application1);
        tester.assertResources("Scaling down since resource usage has gone down significantly",
                               10, 1.2, 44.4, 44.4,
                               tester.autoscale(application1, cluster1));
    }

}

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

        assertTrue("No metrics -> No change", tester.autoscale(application1, cluster1).isEmpty());

        tester.addMeasurements( 0.3f, 60, application1);
        assertTrue("Too few metrics -> No change", tester.autoscale(application1, cluster1).isEmpty());

        tester.addMeasurements( 0.3f, 60, application1);
        tester.assertResources("Scaling up since resource usage is too high",
                               10, 2.5, 23.8, 23.8,
                               tester.autoscale(application1, cluster1));

        tester.assertResources("No new info -> Same result",
                               10, 2.5, 23.8, 23.8,
                               tester.autoscale(application1, cluster1));

        tester.addMeasurements( 0.1f, 120, application1);
        tester.assertResources("Scale down since resource usage has gone down",
                               10, 1.7, 15.9, 15.9,
                               tester.autoscale(application1, cluster1));
    }

}

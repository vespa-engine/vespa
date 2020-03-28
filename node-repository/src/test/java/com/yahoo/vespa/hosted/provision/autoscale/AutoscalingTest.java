// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.google.common.collect.Sets;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.io.IOUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class AutoscalingTest {

    @Test
    public void testAutoscalingSingleContentGroup() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);

        assertTrue("No measurements -> No change", tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.addMeasurements(Resource.cpu, 0.25f, 1f, 60, application1);
        assertTrue("Too few measurements -> No change", tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.addMeasurements(Resource.cpu, 0.25f, 1f, 60, application1);
        AllocatableClusterResources scaledResources = tester.assertResources("Scaling up since resource usage is too high",
                                                                             15, 1, 1.3,  28.6, 28.6,
                                                                             tester.autoscale(application1, cluster1.id(), min, max));

        tester.deploy(application1, cluster1, scaledResources);
        assertTrue("Cluster in flux -> No further change", tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.deactivateRetired(application1, cluster1, scaledResources);
        tester.addMeasurements(Resource.cpu, 0.8f, 1f, 3, application1);
        assertTrue("Load change is large, but insufficient measurements for new config -> No change",
                   tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.addMeasurements(Resource.cpu,  0.19f, 1f, 100, application1);
        assertEquals("Load change is small -> No change", Optional.empty(), tester.autoscale(application1, cluster1.id(), min, max));

        tester.addMeasurements(Resource.cpu,  0.1f, 1f, 120, application1);
        tester.assertResources("Scaling down to minimum since usage has gone down significantly",
                               14, 1, 1.0, 30.8, 30.8,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    /** We prefer fewer nodes for container clusters as (we assume) they all use the same disk and memory */
    @Test
    public void testAutoscalingSingleContainerGroup() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);

        tester.addMeasurements(Resource.cpu, 0.25f, 1f, 120, application1);
        AllocatableClusterResources scaledResources = tester.assertResources("Scaling up since cpu usage is too high",
                                                                             7, 1, 2.6,  80.0, 80.0,
                                                                             tester.autoscale(application1, cluster1.id(), min, max));

        tester.deploy(application1, cluster1, scaledResources);
        tester.deactivateRetired(application1, cluster1, scaledResources);

        tester.addMeasurements(Resource.cpu,  0.1f, 1f, 120, application1);
        tester.assertResources("Scaling down since cpu usage has gone down",
                               4, 1, 2.4, 68.6, 68.6,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void testAutoscalingRespectsUpperLimit() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources( 6, 1, new NodeResources(2.4, 78, 79, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.addMeasurements(Resource.cpu,    0.25f, 120, application1);
        tester.addMeasurements(Resource.memory, 0.95f, 120, application1);
        tester.addMeasurements(Resource.disk,   0.95f, 120, application1);
        tester.assertResources("Scaling up to limit since resource usage is too high",
                               6, 1, 2.4,  78.0, 79.0,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void testAutoscalingRespectsLowerLimit() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 3, 1, new NodeResources(1.8, 7.4, 8.5, 1));
        ClusterResources max = new ClusterResources( 6, 1, new NodeResources(2.4, 78, 79, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.addMeasurements(Resource.cpu,    0.05f, 120, application1);
        tester.addMeasurements(Resource.memory, 0.05f, 120, application1);
        tester.addMeasurements(Resource.disk,   0.05f, 120, application1);
        tester.assertResources("Scaling down to limit since resource usage is low",
                               3, 1, 1.8,  7.4, 8.5,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void testAutoscalingRespectsGroupLimit() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(18, 6, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 5, resources);
        tester.addMeasurements(Resource.cpu,  0.25f, 1f, 120, application1);
        tester.assertResources("Scaling up since resource usage is too high",
                               6, 6, 2.5,  80.0, 80.0,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    /** This condition ensures we get recommendation suggestions when deactivated */
    @Test
    public void testAutoscalingLimitsAreIgnoredIfMinEqualsMax() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = min;
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.addMeasurements(Resource.cpu,  0.25f, 1f, 120, application1);
        tester.assertResources("Scaling up since resource usage is too high",
                               7, 1, 2.6,  80.0, 80.0,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void testAutoscalingGroupSize1() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 20, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 5, resources);
        tester.addMeasurements(Resource.cpu,  0.25f, 1f, 120, application1);
        tester.assertResources("Scaling up since resource usage is too high",
                               7, 7, 2.5,  80.0, 80.0,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void testAutoscalingGroupSize3() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 3, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(21, 7, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 6, 2, resources);
        tester.addMeasurements(Resource.cpu,  0.22f, 1f, 120, application1);
        tester.assertResources("Scaling up since resource usage is too high",
                               9, 3, 2.7,  83.3, 83.3,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void testAutoscalingAvoidsIllegalConfigurations() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 6, 1, resources);
        tester.addMeasurements(Resource.memory,  0.02f, 1f, 120, application1);
        tester.assertResources("Scaling down",
                               6, 1, 3.0, 4.0, 100.0,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void testAutoscalingAws() {
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        List<Flavor> flavors = new ArrayList<>();
        flavors.add(new Flavor("aws-xlarge", new NodeResources(3, 200, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        flavors.add(new Flavor("aws-large",  new NodeResources(3, 150, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        flavors.add(new Flavor("aws-medium", new NodeResources(3, 100, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        flavors.add(new Flavor("aws-small",  new NodeResources(3,  80, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        AutoscalingTester tester = new AutoscalingTester(new Zone(CloudName.from("aws"), SystemName.main,
                                                                  Environment.prod, RegionName.from("us-east")),
                                                         flavors);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy (Why 83 Gb memory? See AutoscalingTester.MockHostResourcesCalculator
        tester.deploy(application1, cluster1, 5, 1, new NodeResources(3, 103, 100, 1));

        tester.addMeasurements(Resource.memory, 0.9f, 0.6f, 120, application1);
        AllocatableClusterResources scaledResources = tester.assertResources("Scaling up since resource usage is too high.",
                                                                  8, 1, 3,  83, 34.3,
                                                                  tester.autoscale(application1, cluster1.id(), min, max));

        tester.deploy(application1, cluster1, scaledResources);
        tester.deactivateRetired(application1, cluster1, scaledResources);

        tester.addMeasurements(Resource.memory, 0.3f, 0.6f, 1000, application1);
        tester.assertResources("Scaling down since resource usage has gone down",
                               5, 1, 3, 83, 36,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

}

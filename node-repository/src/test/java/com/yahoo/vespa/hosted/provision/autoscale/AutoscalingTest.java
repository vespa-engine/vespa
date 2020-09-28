// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class AutoscalingTest {

    @Test
    public void test_autoscaling_single_content_group() {
        NodeResources hostResources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1,
                                                     new NodeResources(1, 1, 1, 1, NodeResources.DiskSpeed.any));
        ClusterResources max = new ClusterResources(20, 1,
                                                    new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any));
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, hostResources);

        assertTrue("No measurements -> No change", tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.addMeasurements(Resource.cpu, 0.25f, 1f, 59, application1);
        assertTrue("Too few measurements -> No change", tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.addMeasurements(Resource.cpu, 0.25f, 1f, 60, application1);
        ClusterResources scaledResources = tester.assertResources("Scaling up since resource usage is too high",
                                                                  14, 1, 1.3,  30.8, 30.8,
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

    @Test
    public void autoscaling_handles_disk_setting_changes() {
        NodeResources hostResources = new NodeResources(3, 100, 100, 1, NodeResources.DiskSpeed.slow);
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy with slow
        tester.deploy(application1, cluster1, 5, 1, hostResources);
        tester.nodeRepository().getNodes(application1).stream()
              .allMatch(n -> n.allocation().get().requestedResources().diskSpeed() == NodeResources.DiskSpeed.slow);

        tester.addMeasurements(Resource.cpu, 0.25f, 1f, 120, application1);
        // Changing min and max from slow to any
        ClusterResources min = new ClusterResources( 2, 1,
                                                     new NodeResources(1, 1, 1, 1, NodeResources.DiskSpeed.any));
        ClusterResources max = new ClusterResources(20, 1,
                                                    new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any));
        ClusterResources scaledResources = tester.assertResources("Scaling up since resource usage is too high",
                                                                  14, 1, 1.3,  30.8, 30.8,
                                                                  tester.autoscale(application1, cluster1.id(), min, max));
        assertEquals("Disk speed from min/max is used",
                     NodeResources.DiskSpeed.any, scaledResources.nodeResources().diskSpeed());
        tester.deploy(application1, cluster1, scaledResources);
        tester.nodeRepository().getNodes(application1).stream()
              .allMatch(n -> n.allocation().get().requestedResources().diskSpeed() == NodeResources.DiskSpeed.any);
    }

    /** We prefer fewer nodes for container clusters as (we assume) they all use the same disk and memory */
    @Test
    public void test_autoscaling_single_container_group() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);

        tester.addMeasurements(Resource.cpu, 0.25f, 1f, 120, application1);
        ClusterResources scaledResources = tester.assertResources("Scaling up since cpu usage is too high",
                                                                  7, 1, 2.5,  80.0, 80.0,
                                                                  tester.autoscale(application1, cluster1.id(), min, max));

        tester.deploy(application1, cluster1, scaledResources);
        tester.deactivateRetired(application1, cluster1, scaledResources);

        tester.addMeasurements(Resource.cpu,  0.1f, 1f, 120, application1);
        tester.assertResources("Scaling down since cpu usage has gone down",
                               4, 1, 2.5, 68.6, 68.6,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void autoscaling_respects_upper_limit() {
        NodeResources hostResources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources( 6, 1, new NodeResources(2.4, 78, 79, 1));
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1,
                      new NodeResources(1.9, 70, 70, 1));
        tester.addMeasurements(Resource.cpu,    0.25f, 120, application1);
        tester.addMeasurements(Resource.memory, 0.95f, 120, application1);
        tester.addMeasurements(Resource.disk,   0.95f, 120, application1);
        tester.assertResources("Scaling up to limit since resource usage is too high",
                               6, 1, 2.4,  78.0, 79.0,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void autoscaling_respects_lower_limit() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 4, 1, new NodeResources(1.8, 7.4, 8.5, 1));
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
                               4, 1, 1.8,  7.4, 10.0,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void autoscaling_respects_group_limit() {
        NodeResources hostResources = new NodeResources(30.0, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(18, 6, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 5, new NodeResources(3.0, 10, 10, 1));
        tester.addMeasurements(Resource.cpu,  0.3f, 1f, 240, application1);
        tester.assertResources("Scaling up since resource usage is too high",
                               6, 6, 3.6,  8.0, 10.0,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void test_autoscaling_limits_when_min_equals_xax() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = min;
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.addMeasurements(Resource.cpu,  0.25f, 1f, 120, application1);
        assertTrue(tester.autoscale(application1, cluster1.id(), min, max).isEmpty());
    }

    @Test
    public void suggestions_ignores_limits() {
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
                               7, 1, 2.5,  80.0, 80.0,
                               tester.suggest(application1, cluster1.id(), min, max));
    }

    @Test
    public void test_autoscaling_group_size_1() {
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
    public void test_autoscalinggroupsize_by_cpu() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 3, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(21, 7, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 6, 2, resources);
        tester.addMeasurements(Resource.cpu,  0.25f, 1f, 120, application1);
        tester.assertResources("Scaling up since resource usage is too high, changing to 1 group is cheaper",
                               8, 1, 2.7,  83.3, 83.3,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void test_autoscaling_group_size() {
        NodeResources hostResources = new NodeResources(100, 1000, 1000, 100);
        ClusterResources min = new ClusterResources( 3, 2, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(30, 30, new NodeResources(100, 100, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 6, 2, new NodeResources(10, 100, 100, 1));
        tester.addMeasurements(Resource.memory,  1.0f, 1f, 1000, application1);
        tester.assertResources("Increase group size to reduce memory load",
                               8, 2, 12.9,  89.3, 62.5,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void autoscaling_avoids_illegal_configurations() {
        NodeResources hostResources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 6, 1, hostResources);
        tester.addMeasurements(Resource.memory,  0.02f, 0.95f, 120, application1);
        tester.assertResources("Scaling down",
                               6, 1, 2.8, 4.0, 95.0,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    @Test
    public void real_resources_are_taken_into_account() {
        NodeResources hostResources = new NodeResources(60, 100, 1000, 10);
        ClusterResources min = new ClusterResources(2, 1, new NodeResources( 2,  20,  200, 1));
        ClusterResources max = new ClusterResources(4, 1, new NodeResources(60, 100, 1000, 1));

        { // No memory tax
            AutoscalingTester tester = new AutoscalingTester(hostResources, new OnlySubtractingWhenForecastingCalculator(0));

            ApplicationId application1 = tester.applicationId("app1");
            ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

            tester.deploy(application1, cluster1, min);
            tester.addMeasurements(Resource.cpu, 1.0f, 1000, application1);
            tester.addMeasurements(Resource.memory, 1.0f, 1000, application1);
            tester.addMeasurements(Resource.disk, 0.7f, 1000, application1);
            tester.assertResources("Scaling up",
                                   4, 1, 7.0, 20, 200,
                                   tester.autoscale(application1, cluster1.id(), min, max));
        }

        { // 15 Gb memory tax
            AutoscalingTester tester = new AutoscalingTester(hostResources, new OnlySubtractingWhenForecastingCalculator(15));

            ApplicationId application1 = tester.applicationId("app1");
            ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

            tester.deploy(application1, cluster1, min);
            tester.addMeasurements(Resource.cpu, 1.0f, 1000, application1);
            tester.addMeasurements(Resource.memory, 1.0f, 1000, application1);
            tester.addMeasurements(Resource.disk, 0.7f, 1000, application1);
            tester.assertResources("Scaling up",
                                   4, 1, 7.0, 34, 200,
                                   tester.autoscale(application1, cluster1.id(), min, max));
        }
    }

    @Test
    public void test_autoscaling_without_host_sharing() {
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        List<Flavor> flavors = new ArrayList<>();
        flavors.add(new Flavor("aws-xlarge", new NodeResources(3, 200, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        flavors.add(new Flavor("aws-large",  new NodeResources(3, 150, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        flavors.add(new Flavor("aws-medium", new NodeResources(3, 100, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        flavors.add(new Flavor("aws-small",  new NodeResources(3,  80, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        AutoscalingTester tester = new AutoscalingTester(new Zone(Cloud.builder()
                                                                       .dynamicProvisioning(true)
                                                                       .allowHostSharing(false)
                                                                       .build(),
                                                                  SystemName.main,
                                                                  Environment.prod, RegionName.from("us-east")),
                                                         flavors);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy (Why 103 Gb memory? See AutoscalingTester.MockHostResourcesCalculator
        tester.deploy(application1, cluster1, 5, 1, new NodeResources(3, 103, 100, 1));

        tester.addMeasurements(Resource.memory, 0.9f, 0.6f, 120, application1);
        ClusterResources scaledResources = tester.assertResources("Scaling up since resource usage is too high.",
                                                                  8, 1, 3,  83, 34.3,
                                                                  tester.autoscale(application1, cluster1.id(), min, max));

        tester.deploy(application1, cluster1, scaledResources);
        tester.deactivateRetired(application1, cluster1, scaledResources);

        tester.addMeasurements(Resource.memory, 0.3f, 0.6f, 1000, application1);
        tester.assertResources("Scaling down since resource usage has gone down",
                               5, 1, 3, 83, 36,
                               tester.autoscale(application1, cluster1.id(), min, max));
    }

    /**
     * This calculator subtracts the memory tax when forecasting overhead, but not when actually
     * returning information about nodes. This is allowed because the forecast is a *worst case*.
     * It is useful here because it ensures that we end up with the same real (and therefore target)
     * resources regardless of tax which makes it easier to compare behavior with different tax levels.
     */
    private static class OnlySubtractingWhenForecastingCalculator implements HostResourcesCalculator {

        private final int memoryTaxGb;

        public OnlySubtractingWhenForecastingCalculator(int memoryTaxGb) {
            this.memoryTaxGb = memoryTaxGb;
        }

        @Override
        public NodeResources realResourcesOf(Node node, NodeRepository nodeRepository) {
            return node.resources();
        }

        @Override
        public NodeResources advertisedResourcesOf(Flavor flavor) {
            return flavor.resources();
        }

        @Override
        public NodeResources requestToReal(NodeResources resources) {
            return resources.withMemoryGb(resources.memoryGb() - memoryTaxGb);
        }

        @Override
        public NodeResources realToRequest(NodeResources resources) {
            return resources.withMemoryGb(resources.memoryGb() + memoryTaxGb);
        }

    }

}

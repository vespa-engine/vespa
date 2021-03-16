// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

        tester.clock().advance(Duration.ofDays(1));
        assertTrue("No measurements -> No change", tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.addCpuMeasurements(0.25f, 1f, 59, application1);
        assertTrue("Too few measurements -> No change", tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.clock().advance(Duration.ofDays(1));
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addCpuMeasurements(0.25f, 1f, 120, application1);
        ClusterResources scaledResources = tester.assertResources("Scaling up since resource usage is too high",
                                                                  14, 1, 1.4,  30.8, 30.8,
                                                                  tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.deploy(application1, cluster1, scaledResources);
        assertTrue("Cluster in flux -> No further change", tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.deactivateRetired(application1, cluster1, scaledResources);

        tester.clock().advance(Duration.ofDays(2));
        tester.addCpuMeasurements(0.8f, 1f, 3, application1);
        assertTrue("Load change is large, but insufficient measurements for new config -> No change",
                   tester.autoscale(application1, cluster1.id(), min, max).isEmpty());

        tester.addCpuMeasurements(0.19f, 1f, 100, application1);
        assertEquals("Load change is small -> No change", Optional.empty(), tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.addCpuMeasurements(0.1f, 1f, 120, application1);
        tester.assertResources("Scaling down to minimum since usage has gone down significantly",
                               15, 1, 1.0, 28.6, 28.6,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

        var events = tester.nodeRepository().applications().get(application1).get().cluster(cluster1.id()).get().scalingEvents();
    }

    /** We prefer fewer nodes for container clusters as (we assume) they all use the same disk and memory */
    @Test
    public void test_autoscaling_single_container_group() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only

        tester.addCpuMeasurements(0.25f, 1f, 120, application1);
        ClusterResources scaledResources = tester.assertResources("Scaling up since cpu usage is too high",
                                                                  7, 1, 2.5,  80.0, 80.0,
                                                                  tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.deploy(application1, cluster1, scaledResources);
        tester.deactivateRetired(application1, cluster1, scaledResources);

        tester.addCpuMeasurements(0.1f, 1f, 120, application1);
        tester.assertResources("Scaling down since cpu usage has gone down",
                               4, 1, 2.5, 68.6, 68.6,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void autoscaling_handles_disk_setting_changes() {
        NodeResources hostResources = new NodeResources(3, 100, 100, 1, NodeResources.DiskSpeed.slow);
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy with slow
        tester.deploy(application1, cluster1, 5, 1, hostResources);
        tester.nodeRepository().nodes().list().owner(application1).stream()
              .allMatch(n -> n.allocation().get().requestedResources().diskSpeed() == NodeResources.DiskSpeed.slow);

        tester.clock().advance(Duration.ofDays(2));
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addCpuMeasurements(0.25f, 1f, 120, application1);
        // Changing min and max from slow to any
        ClusterResources min = new ClusterResources( 2, 1,
                                                     new NodeResources(1, 1, 1, 1, NodeResources.DiskSpeed.any));
        ClusterResources max = new ClusterResources(20, 1,
                                                    new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any));
        ClusterResources scaledResources = tester.assertResources("Scaling up since resource usage is too high",
                                                                  14, 1, 1.4,  30.8, 30.8,
                                                                  tester.autoscale(application1, cluster1.id(), min, max).target());
        assertEquals("Disk speed from min/max is used",
                     NodeResources.DiskSpeed.any, scaledResources.nodeResources().diskSpeed());
        tester.deploy(application1, cluster1, scaledResources);
        tester.nodeRepository().nodes().list().owner(application1).stream()
              .allMatch(n -> n.allocation().get().requestedResources().diskSpeed() == NodeResources.DiskSpeed.any);
    }

    @Test
    public void autoscaling_target_preserves_any() {
        NodeResources hostResources = new NodeResources(3, 100, 100, 1);
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // Initial deployment
        NodeResources resources = new NodeResources(1, 10, 10, 1);
        var min = new ClusterResources( 2, 1, resources.with(NodeResources.DiskSpeed.any));
        var max = new ClusterResources( 10, 1, resources.with(NodeResources.DiskSpeed.any));
        tester.deploy(application1, cluster1, Capacity.from(min, max));

        // Redeployment without target: Uses current resource numbers with *requested* non-numbers (i.e disk-speed any)
        assertTrue(tester.nodeRepository().applications().get(application1).get().cluster(cluster1.id()).get().targetResources().isEmpty());
        tester.deploy(application1, cluster1, Capacity.from(min, max));
        assertEquals(NodeResources.DiskSpeed.any,
                     tester.nodeRepository().nodes().list().owner(application1).cluster(cluster1.id()).first().get()
                           .allocation().get().requestedResources().diskSpeed());

        // Autoscaling: Uses disk-speed any as well
        tester.clock().advance(Duration.ofDays(2));
        tester.addCpuMeasurements(0.8f, 1f, 120, application1);
        Autoscaler.Advice advice = tester.autoscale(application1, cluster1.id(), min, max);
        assertEquals(NodeResources.DiskSpeed.any, advice.target().get().nodeResources().diskSpeed());


    }

    @Test
    public void autoscaling_respects_upper_limit() {
        NodeResources hostResources = new NodeResources(6, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources( 6, 1, new NodeResources(2.4, 78, 79, 1));
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1,
                      new NodeResources(1.9, 70, 70, 1));
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addMeasurements(0.25f, 0.95f, 0.95f, 0, 120, application1);
        tester.assertResources("Scaling up to limit since resource usage is too high",
                               6, 1, 2.4,  78.0, 79.0,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void autoscaling_respects_lower_limit() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 4, 1, new NodeResources(1.8, 7.4, 8.5, 1));
        ClusterResources max = new ClusterResources( 6, 1, new NodeResources(2.4, 78, 79, 1));
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.addMeasurements(0.05f, 0.05f, 0.05f,  0, 120, application1);
        tester.assertResources("Scaling down to limit since resource usage is low",
                               4, 1, 1.8,  7.4, 10.0,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
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
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addCpuMeasurements( 0.3f, 1f, 240, application1);
        tester.assertResources("Scaling up since resource usage is too high",
                               6, 6, 3.6,  8.0, 10.0,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void test_autoscaling_limits_when_min_equals_max() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = min;
        AutoscalingTester tester = new AutoscalingTester(resources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.clock().advance(Duration.ofDays(1));
        tester.addCpuMeasurements(0.25f, 1f, 120, application1);
        assertTrue(tester.autoscale(application1, cluster1.id(), min, max).isEmpty());
    }

    @Test
    public void suggestions_ignores_limits() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = min;
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addCpuMeasurements(0.25f, 1f, 120, application1);
        tester.assertResources("Scaling up since resource usage is too high",
                               7, 1, 2.5,  80.0, 80.0,
                               tester.suggest(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void not_using_out_of_service_measurements() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources(2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(5, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 2, 1, resources);
        tester.addMeasurements(0.5f, 0.6f, 0.7f, 1, false, true, 120, application1);
        assertTrue("Not scaling up since nodes were measured while cluster was unstable",
                   tester.autoscale(application1, cluster1.id(), min, max).isEmpty());
    }

    @Test
    public void not_using_unstable_measurements() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources(2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(5, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 2, 1, resources);
        tester.addMeasurements(0.5f, 0.6f, 0.7f, 1, true, false, 120, application1);
        assertTrue("Not scaling up since nodes were measured while cluster was unstable",
                   tester.autoscale(application1, cluster1.id(), min, max).isEmpty());
    }

    @Test
    public void test_autoscaling_group_size_1() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 20, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 5, 5, resources);
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addCpuMeasurements(0.25f, 1f, 120, application1);
        tester.assertResources("Scaling up since resource usage is too high",
                               7, 7, 2.5,  80.0, 80.0,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void test_autoscaling_groupsize_by_cpu() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 3, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(21, 7, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 6, 2, resources);
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addCpuMeasurements(0.25f, 1f, 120, application1);
        tester.assertResources("Scaling up since resource usage is too high, changing to 1 group is cheaper",
                               8, 1, 2.7,  83.3, 83.3,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
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
        tester.clock().advance(Duration.ofDays(1));
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addMemMeasurements(1.0f, 1f, 1000, application1);
        tester.assertResources("Increase group size to reduce memory load",
                               8, 2, 13.6,  89.3, 62.5,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void autoscaling_avoids_illegal_configurations() {
        NodeResources hostResources = new NodeResources(6, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy
        tester.deploy(application1, cluster1, 6, 1, hostResources.withVcpu(hostResources.vcpu() / 2));
        tester.clock().advance(Duration.ofDays(2));
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addMemMeasurements(0.02f, 0.95f, 120, application1);
        tester.assertResources("Scaling down",
                               6, 1, 2.9, 4.0, 95.0,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void scaling_down_only_after_delay() {
        NodeResources hostResources = new NodeResources(6, 100, 100, 1);
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        AutoscalingTester tester = new AutoscalingTester(hostResources);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        tester.deploy(application1, cluster1, 6, 1, hostResources.withVcpu(hostResources.vcpu() / 2));
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only

        // No autoscaling as it is too soon to scale down after initial deploy (counting as a scaling event)
        tester.addMemMeasurements(0.02f, 0.95f, 120, application1);
        assertTrue(tester.autoscale(application1, cluster1.id(), min, max).target().isEmpty());

        // Trying the same later causes autoscaling
        tester.clock().advance(Duration.ofDays(2));
        tester.addMemMeasurements(0.02f, 0.95f, 120, application1);
        tester.assertResources("Scaling down",
                               6, 1, 2.9, 4.0, 95.0,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void test_autoscaling_considers_real_resources() {
        NodeResources hostResources = new NodeResources(60, 100, 1000, 10);
        ClusterResources min = new ClusterResources(2, 1, new NodeResources( 2,  20,  200, 1));
        ClusterResources max = new ClusterResources(4, 1, new NodeResources(60, 100, 1000, 1));

        { // No memory tax
            AutoscalingTester tester = new AutoscalingTester(hostResources, new OnlySubtractingWhenForecastingCalculator(0));

            ApplicationId application1 = tester.applicationId("app1");
            ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

            tester.deploy(application1, cluster1, min);
            tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
            tester.addMeasurements(1.0f, 1.0f, 0.7f, 0, 1000, application1);
            tester.assertResources("Scaling up",
                                   4, 1, 7.4, 20, 200,
                                   tester.autoscale(application1, cluster1.id(), min, max).target());
        }

        { // 15 Gb memory tax
            AutoscalingTester tester = new AutoscalingTester(hostResources, new OnlySubtractingWhenForecastingCalculator(15));

            ApplicationId application1 = tester.applicationId("app1");
            ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

            tester.deploy(application1, cluster1, min);
            tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
            tester.addMeasurements(1.0f, 1.0f, 0.7f, 0, 1000, application1);
            tester.assertResources("Scaling up",
                                   4, 1, 7.4, 34, 200,
                                   tester.autoscale(application1, cluster1.id(), min, max).target());
        }
    }

    @Test
    public void test_autoscaling_with_dynamic_provisioning() {
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        ClusterResources max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        List<Flavor> flavors = new ArrayList<>();
        flavors.add(new Flavor("aws-xlarge", new NodeResources(3, 200, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        flavors.add(new Flavor("aws-large",  new NodeResources(3, 150, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        flavors.add(new Flavor("aws-medium", new NodeResources(3, 100, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        flavors.add(new Flavor("aws-small",  new NodeResources(3,  80, 100, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote)));
        AutoscalingTester tester = new AutoscalingTester(new Zone(Cloud.builder()
                                                                       .dynamicProvisioning(true)
                                                                       .build(),
                                                                  SystemName.main,
                                                                  Environment.prod, RegionName.from("us-east")),
                                                         flavors);

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.content, "cluster1");

        // deploy (Why 103 Gb memory? See AutoscalingTester.MockHostResourcesCalculator
        tester.deploy(application1, cluster1, 5, 1, new NodeResources(3, 103, 100, 1));

        tester.clock().advance(Duration.ofDays(2));
        tester.addMemMeasurements(0.9f, 0.6f, 120, application1);
        ClusterResources scaledResources = tester.assertResources("Scaling up since resource usage is too high.",
                                                                  8, 1, 3,  83, 34.3,
                                                                  tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.deploy(application1, cluster1, scaledResources);
        tester.deactivateRetired(application1, cluster1, scaledResources);

        tester.clock().advance(Duration.ofDays(2));
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addMemMeasurements(0.3f, 0.6f, 1000, application1);
        tester.assertResources("Scaling down since resource usage has gone down",
                               6, 1, 3, 83, 28.8,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void test_autoscaling_considers_read_share() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 1, 1, resources);
        ClusterResources max = new ClusterResources(10, 1, resources);
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addCpuMeasurements(0.25f, 1f, 120, application1);

        // (no read share stored)
        tester.assertResources("Advice to scale up since we set aside for bcp by default",
                               7, 1, 3,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.storeReadShare(0.25, 0.5, application1);
        tester.assertResources("Half of global share is the same as the default assumption used above",
                               7, 1, 3,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.storeReadShare(0.5, 0.5, application1);
        tester.assertResources("Advice to scale down since we don't need room for bcp",
                               4, 1, 3,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

    }

    @Test
    public void test_autoscaling_considers_growth_rate() {
        NodeResources resources = new NodeResources(3, 100, 100, 1);
        ClusterResources min = new ClusterResources( 1, 1, resources);
        ClusterResources max = new ClusterResources(10, 1, resources);
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        tester.deploy(application1, cluster1, 5, 1, resources);
        tester.addQueryRateMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
        tester.addCpuMeasurements(0.25f, 1f, 120, application1);

        // (no query rate data)
        tester.assertResources("Advice to scale up since we assume we need 2x cpu for growth when no data",
                               7, 1, 3,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.setScalingDuration(application1, cluster1.id(), Duration.ofMinutes(5));
        tester.addQueryRateMeasurements(application1, cluster1.id(),
                                        100,
                                        t -> 10.0 + (t < 50 ? t : 100 - t));
        tester.assertResources("Advice to scale down since observed growth is much slower than scaling time",
                               4, 1, 3,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.clearQueryRateMeasurements(application1, cluster1.id());

        tester.setScalingDuration(application1, cluster1.id(), Duration.ofMinutes(60));
        tester.addQueryRateMeasurements(application1, cluster1.id(),
                                        100,
                                        t -> 10.0 + (t < 50 ? t * t * t : 125000 - (t - 49) * (t - 49) * (t - 49)));
        tester.assertResources("Advice to scale up since observed growth is much faster than scaling time",
                               10, 1, 3,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void test_autoscaling_considers_query_vs_write_rate() {
        NodeResources minResources = new NodeResources( 1, 100, 100, 1);
        NodeResources midResources = new NodeResources( 5, 100, 100, 1);
        NodeResources maxResources = new NodeResources(10, 100, 100, 1);
        ClusterResources min = new ClusterResources(5, 1, minResources);
        ClusterResources max = new ClusterResources(5, 1, maxResources);
        AutoscalingTester tester = new AutoscalingTester(maxResources.withVcpu(maxResources.vcpu() * 2));

        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");

        tester.deploy(application1, cluster1, 5, 1, midResources);
        tester.addCpuMeasurements(0.4f, 1f, 120, application1);

        // Why twice the query rate at time = 0?
        // This makes headroom for queries doubling, which we want to observe the effect of here

        tester.addLoadMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0, t -> 10.0);
        tester.assertResources("Query and write load is equal -> scale up somewhat",
                               5, 1, 7.3,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.addLoadMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 100.0 : 50.0, t -> 10.0);
        tester.assertResources("Query load is 5x write load -> scale up more",
                               5, 1, 9.7,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.addLoadMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0, t -> 100.0);
        tester.assertResources("Write load is 10x query load -> scale down",
                               5, 1, 3.8,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.addLoadMeasurements(application1, cluster1.id(), 10, t -> t == 0 ? 20.0 : 10.0, t-> 0.0);
        tester.assertResources("Query only -> largest possible",
                               5, 1, 10.0,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());

        tester.addLoadMeasurements(application1, cluster1.id(), 10, t ->  0.0, t -> 10.0);
        tester.assertResources("Write only -> smallest possible",
                               5, 1, 2.1,  100, 100,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
    }

    @Test
    public void test_cd_autoscaling_test() {
        NodeResources resources = new NodeResources(1, 4, 50, 1);
        ClusterResources min = new ClusterResources( 2, 1, resources);
        ClusterResources max = new ClusterResources(3, 1, resources);
        AutoscalingTester tester = new AutoscalingTester(resources.withVcpu(resources.vcpu() * 2));
        ApplicationId application1 = tester.applicationId("application1");
        ClusterSpec cluster1 = tester.clusterSpec(ClusterSpec.Type.container, "cluster1");
        tester.deploy(application1, cluster1, 2, 1, resources);

        tester.addCpuMeasurements(0.5f, 1f, 10, application1);
        tester.addQueryRateMeasurements(application1, cluster1.id(),
                                        500, t -> 0.0);

        tester.assertResources("Advice to scale up since observed growth is much faster than scaling time",
                               3, 1, 1,  4, 50,
                               tester.autoscale(application1, cluster1.id(), min, max).target());
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
        public NodeResources realResourcesOf(Nodelike node, NodeRepository nodeRepository, boolean exclusive) {
            return node.resources();
        }

        @Override
        public NodeResources advertisedResourcesOf(Flavor flavor) {
            return flavor.resources();
        }

        @Override
        public NodeResources requestToReal(NodeResources resources, boolean exclusive) {
            return resources.withMemoryGb(resources.memoryGb() - memoryTaxGb);
        }

        @Override
        public NodeResources realToRequest(NodeResources resources, boolean exclusive) {
            return resources.withMemoryGb(resources.memoryGb() + memoryTaxGb);
        }

        @Override
        public long thinPoolSizeInBase2Gb(NodeType nodeType, boolean sharedHost) { return 0; }

    }

}

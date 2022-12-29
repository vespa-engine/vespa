// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeResources.DiskSpeed;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
import com.yahoo.config.provision.NodeResources.StorageType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.applications.AutoscalingStatus;
import com.yahoo.vespa.hosted.provision.provisioning.CapacityPolicies;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class AutoscalingTest {

    @Test
    public void test_autoscaling_single_content_group() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();

        fixture.loader().applyCpuLoad(0.7f, 10);
        var scaledResources = fixture.tester().assertResources("Scaling up since resource usage is too high",
                                                               7, 1, 4.6,  11.1, 55.1,
                                                               fixture.autoscale());

        fixture.deploy(Capacity.from(scaledResources));
        assertEquals("Cluster in flux -> No further change", AutoscalingStatus.Status.waiting, fixture.autoscale().reason().status());

        fixture.deactivateRetired(Capacity.from(scaledResources));

        fixture.loader().applyCpuLoad(0.19f, 10);
        assertEquals("Load change is small -> No change", Optional.empty(), fixture.autoscale().target());

        fixture.loader().applyCpuLoad(0.1f, 10);
        assertEquals("Too little time passed for downscaling -> No change", Optional.empty(), fixture.autoscale().target());

        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(0.1f, 10);
        fixture.tester().assertResources("Scaling cpu down since usage has gone down significantly",
                                         6, 1, 1.3, 11.8, 78.6,
                                         fixture.autoscale());
    }

    /** Using too many resources for a short period is proof we should scale up regardless of the time that takes. */
    @Test
    public void test_no_autoscaling_with_no_measurements() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();
        assertTrue(fixture.autoscale().target().isEmpty());
    }

    @Test
    public void test_no_autoscaling_with_no_measurements_exclusive() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(false).build();
        assertTrue(fixture.autoscale().target().isEmpty());
    }

    /** Using too many resources for a short period is proof we should scale up regardless of the time that takes. */
    @Test
    public void test_autoscaling_up_is_fast() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();
        fixture.loader().applyLoad(new Load(0.1, 0.1, 0.1), 3);
        fixture.loader().applyLoad(new Load(1.0, 1.0, 1.0), 1);
        fixture.tester().assertResources("Scaling up since resource usage is too high",
                                         8, 1, 5.3, 17.7, 93.6,
                                         fixture.autoscale());
    }

    @Test
    public void test_container_scaling_down_exclusive() {
        var min = new ClusterResources(2, 1, new NodeResources(4, 8, 50, 0.1));
        var now = new ClusterResources(8, 1, new NodeResources(4, 8, 50, 0.1));
        var max = new ClusterResources(8, 1, new NodeResources(4, 8, 50, 0.1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(false)
                                       .clusterType(ClusterSpec.Type.container)
                                       .initialResources(Optional.of(now))
                                       .capacity(Capacity.from(min, max))
                                       .build();
        fixture.tester().setScalingDuration(fixture.applicationId(), fixture.clusterSpec.id(), Duration.ofMinutes(5));

        fixture.loader().applyLoad(new Load(0.01, 0.38, 0), 5);
        fixture.tester().assertResources("Scaling down",
                                         2, 1, 4, 8, 50,
                                         fixture.autoscale());
    }

    @Test
    public void initial_deployment_with_host_sharing_flag() {
        var min = new ClusterResources(7, 1, new NodeResources(2.0, 10.0, 384.0, 0.1));
        var max = new ClusterResources(7, 1, new NodeResources(2.4, 32.0, 768.0, 0.1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(false)
                                       .capacity(Capacity.from(min, max))
                                       .initialResources(Optional.empty())
                                       .hostSharingFlag()
                                       .build();
        fixture.tester().assertResources("Initial resources at min, since flag turns on host sharing",
                                         7, 1, 2.0, 10.0, 384.0,
                                         fixture.currentResources().advertisedResources());
    }

    @Test
    public void initial_deployment_with_host_sharing_flag_and_too_small_min() {
        var min = new ClusterResources(1, 1, new NodeResources(0.5, 4.0, 10, 0.1));
        var max = new ClusterResources(1, 1, new NodeResources(2.0, 8.0, 50, 0.1));
        var fixture = AutoscalingTester.fixture()
                                       .awsSetup(false, Environment.test)
                                       .clusterType(ClusterSpec.Type.container)
                                       .capacity(Capacity.from(min, max))
                                       .initialResources(Optional.empty())
                                       .hostSharingFlag()
                                       .build();
        fixture.tester().assertResources("Initial resources at min, since flag turns on host sharing",
                                         1, 1, 0.5, 4.0, 10.0,
                                         fixture.currentResources().advertisedResources());
    }

    /** When scaling up, disregard underutilized dimensions (memory here) */
    @Test
    public void test_only_autoscaling_up_quickly() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();
        fixture.loader().applyLoad(new Load(1.0, 0.1, 1.0), 10);
        fixture.tester().assertResources("Scaling up (only) since resource usage is too high",
                                         7, 1, 8.2, 10.7, 99.5,
                                         fixture.autoscale());
    }

    /** When ok to scale down, scale in both directions simultaneously (compare to test_only_autoscaling_up_quickly) */
    @Test
    public void test_scale_in_both_directions_when_ok_to_scale_down() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();
        fixture.tester.clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 0.1, 1.0), 10);
        fixture.tester().assertResources("Scaling cpu and disk up and memory down",
                                         7, 1, 8.2, 4.0, 99.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_scale_in_both_directions_when_ok_to_scale_down_exclusive() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(false).build();
        fixture.tester.clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 0.1, 1.0), 10);
        fixture.tester().assertResources("Scaling cpu and disk up, memory follows",
                                         16, 1, 4, 8.0, 28.3,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_uses_peak() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.loader().applyCpuLoad(0.70, 1);
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.tester().assertResources("Scaling up since peak resource usage is too high",
                                         8, 1, 4.3, 9.5, 47.2,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_uses_peak_exclusive() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(false).build();
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.loader().applyCpuLoad(0.70, 1);
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.tester().assertResources("Scaling up since peak resource usage is too high",
                                         10, 1, 4, 8.0, 22.7,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_uses_peak_preprovisioned() {
        var fixture = AutoscalingTester.fixture().hostCount(15).build();
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.loader().applyCpuLoad(0.70, 1);
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.tester().assertResources("Scaling up since peak resource usage is too high",
                                         9, 1, 3.8, 8.0, 37.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_without_traffic_exclusive() {
        var min = new ClusterResources(1, 1, new NodeResources(0.5, 4, 10, 0.3));
        var now = new ClusterResources(4, 1, new NodeResources(8, 16, 10, 0.3));
        var max = new ClusterResources(4, 1, new NodeResources(16, 32, 50, 0.3));
        var fixture = AutoscalingTester.fixture(min, now, max)
                                       .clusterType(ClusterSpec.Type.container)
                                       .awsProdSetup(false)
                                       .build();
        var duration = fixture.loader().addMeasurements(new Load(0.04, 0.39, 0.01), 20);
        fixture.tester().clock().advance(duration.negated());
        fixture.loader().zeroTraffic(20, 1);
        fixture.tester().assertResources("Scaled down",
                                         2, 1, 2, 16, 10,
                                         fixture.autoscale());
    }

    /** We prefer fewer nodes for container clusters as (we assume) they all use the same disk and memory */
    @Test
    public void test_autoscaling_single_container_group() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).clusterType(ClusterSpec.Type.container).build();

        fixture.loader().applyCpuLoad(0.25f, 120);
        ClusterResources scaledResources = fixture.tester().assertResources("Scaling cpu up",
                                                                  3, 1, 5,  13.3, 66.1,
                                                                  fixture.autoscale());
        fixture.deploy(Capacity.from(scaledResources));
        fixture.deactivateRetired(Capacity.from(scaledResources));
        fixture.loader().applyCpuLoad(0.1f, 120);
        fixture.tester().assertResources("Scaling down since cpu usage has gone down",
                                         3, 1, 2.5, 9.2, 61.1,
                                         fixture.autoscale());
    }

    @Test
    public void autoscaling_handles_disk_setting_changes_exclusive_preprovisioned() {
        var resources = new NodeResources(3, 100, 100, 1, slow);
        var fixture = AutoscalingTester.fixture()
                                       .hostCount(20)
                                       .hostFlavors(resources)
                                       .initialResources(Optional.of(new ClusterResources(5, 1, resources)))
                                       .capacity(Capacity.from(new ClusterResources(5, 1, resources)))
                                       .build();

        assertTrue(fixture.tester().nodeRepository().nodes().list().owner(fixture.applicationId).stream()
                          .allMatch(n -> n.allocation().get().requestedResources().diskSpeed() == slow));

        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(0.25, 120);

        // Changing min and max from slow to any
        ClusterResources min = new ClusterResources( 2, 1,
                                                     new NodeResources(1, 1, 1, 1, DiskSpeed.any));
        ClusterResources max = new ClusterResources(20, 1,
                                                    new NodeResources(100, 1000, 1000, 1, DiskSpeed.any));
        var capacity = Capacity.from(min, max);
        ClusterResources scaledResources = fixture.tester().assertResources("Scaling up",
                                                                            13, 1, 1.5,  26.7, 26.7,
                                                                            fixture.autoscale(capacity));
        assertEquals("Disk speed from new capacity is used",
                     DiskSpeed.any, scaledResources.nodeResources().diskSpeed());
        fixture.deploy(Capacity.from(scaledResources));
        assertTrue(fixture.nodes().stream()
                          .allMatch(n -> n.allocation().get().requestedResources().diskSpeed() == DiskSpeed.any));
    }

    @Test
    public void autoscaling_target_preserves_any() {
        NodeResources resources = new NodeResources(1, 100, 100, 1);
        var capacity = Capacity.from(new ClusterResources( 2, 1, resources.with(DiskSpeed.any)),
                                     new ClusterResources( 10, 1, resources.with(DiskSpeed.any)));
        var fixture = AutoscalingTester.fixture()
                                       .capacity(capacity)
                                       .awsProdSetup(true)
                                       .initialResources(Optional.empty())
                                       .build();

        // Redeployment without target: Uses current resource numbers with *requested* non-numbers (i.e disk-speed any)
        assertTrue(fixture.tester().nodeRepository().applications().get(fixture.applicationId).get().cluster(fixture.clusterSpec.id()).get().target().resources().isEmpty());
        fixture.deploy();
        assertEquals(DiskSpeed.any, fixture.nodes().first().get().allocation().get().requestedResources().diskSpeed());

        // Autoscaling: Uses disk-speed any as well
        fixture.deactivateRetired(capacity);
        fixture.tester().clock().advance(Duration.ofDays(1));
        fixture.loader().applyCpuLoad(0.8, 120);
        assertEquals(DiskSpeed.any, fixture.autoscale(capacity).target().get().nodeResources().diskSpeed());
    }

    @Test
    public void autoscaling_respects_upper_limit() {
        var min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(5, 1, new NodeResources(1.9, 70, 70, 1));
        var max = new ClusterResources( 6, 1, new NodeResources(2.4, 78, 79, 1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(true)
                                       .initialResources(Optional.of(now))
                                       .capacity(Capacity.from(min, max)).build();

        fixture.tester().clock().advance(Duration.ofDays(1));
        fixture.loader().applyLoad(new Load(0.25, 0.95, 0.95), 120);
        fixture.tester().assertResources("Scaling up to limit since resource usage is too high",
                                         6, 1, 2.4,  78.0, 79.0,
                                         fixture.autoscale());
    }

    @Test
    public void autoscaling_respects_lower_limit() {
        var min = new ClusterResources( 4, 1, new NodeResources(1.8, 7.4, 8.5, 1));
        var max = new ClusterResources( 6, 1, new NodeResources(2.4, 78, 79, 1));
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).capacity(Capacity.from(min, max)).build();

        // deploy
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.05f, 0.05f, 0.05f),  120);
        fixture.tester().assertResources("Scaling down to limit since resource usage is low",
                                         4, 1, 1.8,  7.4, 23.5,
                                         fixture.autoscale());
    }

    @Test
    public void autoscaling_with_unspecified_resources_use_defaults_exclusive() {
        var min = new ClusterResources( 2, 1, NodeResources.unspecified());
        var max = new ClusterResources( 6, 1, NodeResources.unspecified());
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(false)
                                       .initialResources(Optional.empty())
                                       .capacity(Capacity.from(min, max))
                                       .build();

        NodeResources defaultResources =
                new CapacityPolicies(fixture.tester().nodeRepository()).defaultNodeResources(fixture.clusterSpec, fixture.applicationId);

        fixture.tester().assertResources("Min number of nodes and default resources",
                                         2, 1, defaultResources,
                                         fixture.nodes().toResources());
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.25, 0.95, 0.95), 120);
        fixture.tester().assertResources("Scaling up",
                                         5, 1,
                                         defaultResources.vcpu(), defaultResources.memoryGb(), defaultResources.diskGb(),
                                         fixture.autoscale());
    }

    @Test
    public void autoscaling_respects_group_limit() {
        var min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(5, 5, new NodeResources(3.0, 10, 10, 1));
        var max = new ClusterResources(18, 6, new NodeResources(100, 1000, 1000, 1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(true)
                                       .initialResources(Optional.of(now))
                                       .capacity(Capacity.from(min, max))
                                       .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(0.4, 240);
        fixture.tester().assertResources("Scaling cpu up",
                                         6, 6, 5.0,  8.1, 10.0,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_limits_when_min_equals_max() {
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).capacity(Capacity.from(min, min)).build();

        fixture.tester().clock().advance(Duration.ofDays(1));
        fixture.loader().applyCpuLoad(0.25, 120);
        assertEquals(AutoscalingStatus.Status.unavailable, fixture.autoscale().reason().status());
    }

    @Test
    public void container_prefers_remote_disk_when_no_local_match_exclusive() {
        var resources = new ClusterResources( 2, 1, new NodeResources(3, 100, 50, 1));
        var local  = new NodeResources(3, 100,  75, 1, fast, StorageType.local);
        var remote = new NodeResources(3, 100,  50, 1, fast, StorageType.remote);
        var fixture = AutoscalingTester.fixture()
                                       .dynamicProvisioning(true)
                                       .allowHostSharing(false)
                                       .clusterType(ClusterSpec.Type.container)
                                       .hostFlavors(local, remote)
                                       .capacity(Capacity.from(resources))
                                       .initialResources(Optional.of(new ClusterResources(3, 1, resources.nodeResources())))
                                       .build();

        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.01, 0.01, 0.01), 120);
        Autoscaler.Advice suggestion = fixture.suggest();
        fixture.tester().assertResources("Choosing the remote disk flavor as it has less disk",
                                         2, 1, 3.0,  100.0, 10.0,
                                         suggestion);
        assertEquals("Choosing the remote disk flavor as it has less disk",
                     StorageType.remote, suggestion.target().get().nodeResources().storageType());
    }

    @Test
    public void suggestions_ignores_limits() {
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).capacity(Capacity.from(min, min)).build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(1.0, 120);
        fixture.tester().assertResources("Suggesting above capacity limit",
                                         8, 1, 6.2,  7.6, 37.8,
                                         fixture.tester().suggest(fixture.applicationId, fixture.clusterSpec.id(), min, min));
    }

    @Test
    public void suggestions_ignores_limits_exclusive() {
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var fixture = AutoscalingTester.fixture().awsProdSetup(false).capacity(Capacity.from(min, min)).build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(1.0, 120);
        fixture.tester().assertResources("Suggesting above capacity limit",
                                         13, 1, 4,  8,  13.6,
                                         fixture.tester().suggest(fixture.applicationId, fixture.clusterSpec.id(), min, min));
    }

    @Test
    public void not_using_out_of_service_measurements() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.9, 0.6, 0.7),  1, false, true, 120);
        assertTrue("Not scaling up since nodes were measured while cluster was out of service",
                   fixture.autoscale().target().isEmpty());
    }

    @Test
    public void not_using_unstable_measurements() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.9, 0.6, 0.7),  1, true, false, 120);
        assertTrue("Not scaling up since nodes were measured while cluster was unstable",
                   fixture.autoscale().target().isEmpty());
    }

    @Test
    public void test_autoscaling_group_size_1() {
        var min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(5, 5, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(20, 20, new NodeResources(10, 1000, 1000, 1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(true)
                                       .initialResources(Optional.of(now))
                                       .capacity(Capacity.from(min, max))
                                       .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(0.9, 120);
        fixture.tester().assertResources("Scaling up to 2 nodes, scaling memory and disk down at the same time",
                                         10, 5, 7.7,  40.6, 47.8,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_groupsize_by_cpu_read_dominated() {
        var min = new ClusterResources( 3, 1, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(6, 2, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(21, 7, new NodeResources(100, 1000, 1000, 1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(true)
                                       .initialResources(Optional.of(now))
                                       .capacity(Capacity.from(min, max))
                                       .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        Duration timePassed = fixture.loader().addCpuMeasurements(0.25, 120);
        fixture.tester().clock().advance(timePassed.negated());
        fixture.loader().addLoadMeasurements(10, t -> t == 0 ? 200.0 : 100.0, t -> 10.0);
        fixture.tester().assertResources("Scaling up cpu, others down, changing to 1 group is cheaper",
                                         8, 1, 2.8, 36.2, 56.4,
                                         fixture.autoscale());
    }

    /** Same as above but mostly write traffic, which favors smaller groups */
    @Test
    public void test_autoscaling_groupsize_by_cpu_write_dominated() {
        var min = new ClusterResources( 3, 1, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(6, 2, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(21, 7, new NodeResources(100, 1000, 1000, 1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(true)
                                       .initialResources(Optional.of(now))
                                       .capacity(Capacity.from(min, max))
                                       .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        Duration timePassed = fixture.loader().addCpuMeasurements(0.25, 120);
        fixture.tester().clock().advance(timePassed.negated());
        fixture.loader().addLoadMeasurements(10, t -> t == 0 ? 20.0 : 10.0, t -> 100.0);
        fixture.tester().assertResources("Scaling down since resource usage is too high, changing to 1 group is cheaper",
                                         6, 1, 1.0,  50.7, 79.0,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_group_size() {
        var min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(6, 2, new NodeResources(10, 100, 100, 1));
        var max = new ClusterResources(30, 30, new NodeResources(100, 100, 1000, 1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(true)
                                       .initialResources(Optional.of(now))
                                       .capacity(Capacity.from(min, max))
                                       .build();
        fixture.tester().clock().advance(Duration.ofDays(1));
        fixture.loader().applyMemLoad(1.0, 1000);
        fixture.tester().assertResources("Increase group size to reduce memory load",
                                         8, 2, 13.9,  97.1, 66.6,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_memory_down() {
        var min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(6, 1, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(true)
                                       .initialResources(Optional.of(now))
                                       .capacity(Capacity.from(min, max))
                                       .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.16,  0.02, 0.5), 120);
        fixture.tester().assertResources("Scaling down memory",
                                         6, 1, 3.0, 4.2, 139.9,
                                         fixture.autoscale());
    }

    @Test
    public void scaling_down_only_after_delay() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();
        fixture.loader().applyCpuLoad(0.02, 120);
        assertTrue("Too soon  after initial deployment", fixture.autoscale().target().isEmpty());
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(0.02, 120);
        fixture.tester().assertResources("Scaling down since enough time has passed",
                                         3, 1, 1.0, 25.8, 147.4,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_considers_read_share() {
        var min = new ClusterResources( 1, 1, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(10, 1, new NodeResources(3, 100, 100, 1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(true)
                                       .capacity(Capacity.from(min, max))
                                       .build();

        fixture.tester.clock().advance(Duration.ofDays(1));
        fixture.loader().applyCpuLoad(0.25, 120);
        // (no read share stored)
        fixture.tester().assertResources("Advice to scale up since we set aside for bcp by default",
                                         6, 1, 3,  100, 100,
                                         fixture.autoscale());

        fixture.loader().applyCpuLoad(0.25, 120);
        fixture.storeReadShare(0.25, 0.5);
        fixture.tester().assertResources("Half of global share is the same as the default assumption used above",
                                         6, 1, 3,  100, 100,
                                         fixture.autoscale());

        fixture.tester.clock().advance(Duration.ofDays(1));
        fixture.loader().applyCpuLoad(0.25, 120);
        fixture.storeReadShare(0.5, 0.5);
        fixture.tester().assertResources("Advice to scale down since we don't need room for bcp",
                                         5, 1, 3,  100, 100,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_considers_growth_rate() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();

        fixture.tester().clock().advance(Duration.ofDays(2));
        Duration timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 200.0 : 100.0, t -> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.25, 200);

        fixture.tester().assertResources("Scale up since we assume we need 2x cpu for growth when no data scaling time data",
                                         6, 1, 2.1,  10.6, 66.5,
                                         fixture.autoscale());

        fixture.setScalingDuration(Duration.ofMinutes(5));
        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t -> 100.0 + (t < 50 ? t : 100 - t), t -> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.25, 200);
        fixture.tester().assertResources("Scale down since observed growth is slower than scaling time",
                                         5, 1, 2.1,   13.3, 83.2,
                                         fixture.autoscale());

        fixture.setScalingDuration(Duration.ofMinutes(60));
        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100,
                                                         t -> 100.0 + (t < 50 ? t * t * t : 125000 - (t - 49) * (t - 49) * (t - 49)),
                                                         t -> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.25, 200);
        fixture.tester().assertResources("Scale up since observed growth is faster than scaling time",
                                         6, 1, 2.1,  10.6, 66.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_weights_growth_rate_by_confidence() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();

        double scalingFactor = 1.0/6000; // To make the average query rate low
        fixture.setScalingDuration(Duration.ofMinutes(60));
        fixture.tester().clock().advance(Duration.ofDays(2));
        Duration timeAdded = fixture.loader().addLoadMeasurements(100,
                                                         t -> scalingFactor * (100.0 + (t < 50 ? t * t * t : 125000 - (t - 49) * (t - 49) * (t - 49))),
                                                         t -> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.7, 200);
        fixture.tester().assertResources("Scale up slightly since observed growth is faster than scaling time, but we are not confident",
                                         5, 1, 2.1,  13.3, 83.2,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_considers_query_vs_write_rate() {
        var fixture = AutoscalingTester.fixture().awsProdSetup(true).build();

        fixture.loader().addCpuMeasurements(0.4, 220);

        // Why twice the query rate at time = 0?
        // This makes headroom for queries doubling, which we want to observe the effect of here

        fixture.tester().clock().advance(Duration.ofDays(2));
        var timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 200.0 : 100.0, t -> 100.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        fixture.tester.assertResources("Query and write load is equal -> scale up somewhat",
                                       7, 1, 2,  8.9, 55.5,
                                       fixture.autoscale());

        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 800.0 : 400.0, t -> 100.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        // TODO: Ackhually, we scale down here - why?
        fixture.tester().assertResources("Query load is 4x write load -> scale up more",
                                         6, 1, 2.1,  10.6, 66.5,
                                         fixture.autoscale());

        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 200.0 : 100.0, t -> 1000.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        fixture.tester().assertResources("Write load is 10x query load -> scale down",
                                         5, 1, 1.4,  13.3, 83.2,
                                         fixture.autoscale());

        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 200.0 : 100.0, t-> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        fixture.tester().assertResources("Query only -> largest possible",
                                         7, 1, 3.5,  8.9, 55.5,
                                         fixture.autoscale());

        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t ->  0.0, t -> 100.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        fixture.tester().assertResources("Write only -> smallest possible",
                                         4, 1, 1.1,  17.2, 110.9,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_in_dev_preprovisioned() {
        var fixture = AutoscalingTester.fixture()
                                       .hostCount(5)
                                       .zone(new Zone(Environment.dev, RegionName.from("us-east")))
                                       .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 1.0, 1.0), 200);
        assertTrue("Not attempting to scale up because policies dictate we'll only get one node",
                   fixture.autoscale().target().isEmpty());
    }

    /** Same setup as test_autoscaling_in_dev(), just with required = true */
    @Test
    public void test_autoscaling_in_dev_with_required_resources_preprovisioned() {
        var requiredCapacity =
                Capacity.from(new ClusterResources(2, 1,
                                                   new NodeResources(1, 1, 1, 1, NodeResources.DiskSpeed.any)),
                              new ClusterResources(20, 1,
                                                   new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any)),
                              true,
                              true);

        var fixture = AutoscalingTester.fixture()
                                       .hostCount(5)
                                       .capacity(requiredCapacity)
                                       .zone(new Zone(Environment.dev, RegionName.from("us-east")))
                                       .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 1.0, 1.0), 200);
        fixture.tester().assertResources("We scale even in dev because resources are 'required'",
                                         3, 1, 1.0,  12.3, 62.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_in_dev_with_required_unspecified_resources_preprovisioned() {
        var requiredCapacity =
                Capacity.from(new ClusterResources(1, 1, NodeResources.unspecified()),
                              new ClusterResources(3, 1, NodeResources.unspecified()),
                              true,
                              true);

        var fixture = AutoscalingTester.fixture()
                                       .hostCount(5)
                                       .capacity(requiredCapacity)
                                       .zone(new Zone(Environment.dev, RegionName.from("us-east")))
                                       .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 1.0, 1.0), 200);
        fixture.tester().assertResources("We scale even in dev because resources are required",
                                         3, 1, 1.5,  8, 50,
                                         fixture.autoscale());
    }

    @Test
    public void test_changing_exclusivity() {
        var min = new ClusterResources( 2, 1, new NodeResources(  1,    4,  100, 1));
        var max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        var fixture = AutoscalingTester.fixture()
                                       .awsProdSetup(true)
                                       .cluster(clusterSpec(true))
                                       .capacity(Capacity.from(min, max))
                                       .initialResources(Optional.empty())
                                       .build();
        fixture.tester().assertResources("Initial deployment at minimum",
                                         2, 1, 2, 4, 100,
                                         fixture.currentResources().advertisedResources());

        fixture.tester().deploy(fixture.applicationId(), clusterSpec(false), fixture.capacity());
        fixture.tester().assertResources("With non-exclusive nodes, a better solution is " +
                                         "50% more nodes with half the cpu",
                                         3, 1, 1, 4, 145.6,
                                         fixture.autoscale());

        fixture.tester().deploy(fixture.applicationId(), clusterSpec(true), fixture.capacity());
        fixture.tester().assertResources("Reverts to the initial resources",
                                         2, 1, 2, 4, 100,
                                         fixture.currentResources().advertisedResources());
    }

    private ClusterSpec clusterSpec(boolean exclusive) {
        return ClusterSpec.request(ClusterSpec.Type.container,
                                   ClusterSpec.Id.from("test")).vespaVersion("8.1.2")
                          .exclusive(exclusive)
                          .build();
    }

}

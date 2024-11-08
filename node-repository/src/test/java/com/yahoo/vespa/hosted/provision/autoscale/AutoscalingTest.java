// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeResources.DiskSpeed;
import com.yahoo.config.provision.NodeResources.StorageType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.provisioning.DynamicProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class AutoscalingTest {

    @Test
    public void test_autoscaling_with_gpu() {
        var resources = new NodeResources(8, 32, 225, 0.1, fast, StorageType.local, NodeResources.Architecture.x86_64, new NodeResources.GpuResources(1, 16));
        var min = new ClusterResources( 8, 1, resources);
        var now = new ClusterResources(12, 1, resources);
        var max = new ClusterResources(12, 1, resources);
        var fixture = DynamicProvisioningTester.fixture()
                .awsProdSetup(true)
                .clusterType(ClusterSpec.Type.container)
                .initialResources(Optional.of(now))
                .capacity(Capacity.from(min, max))
                .build();
        fixture.tester.clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.8f, 0.17, 0.12, 0, 0), 1, true, true, 100);
        var result = fixture.autoscale();
        assertTrue(result.resources().isEmpty());
        assertEquals(Autoscaling.Status.insufficient, result.status());
    }

    @Test
    public void test_autoscaling_nodes_only() {
        var resources = new NodeResources(16, 32, 200, 0.1);
        var min = new ClusterResources( 8, 1, resources);
        var now = new ClusterResources(12, 1, resources.with(StorageType.remote));
        var max = new ClusterResources(12, 1, resources);
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .clusterType(ClusterSpec.Type.content)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.tester.clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.8f, 0.17, 0.12, 0, 0), 1, true, true, 100);
        var result = fixture.autoscale();
        assertTrue(result.resources().isEmpty());
        assertEquals(Autoscaling.Status.insufficient, result.status());

        fixture.tester.clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.08f, 0.17, 0.12, 0, 0), 1, true, true, 100);
        fixture.tester().assertResources("Scaling down",
                                         8, 1, 16, 32, 200,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_single_content_group() {
        var now = new ClusterResources(5, 1, new NodeResources(2, 16, 750, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .build();

        fixture.loader().applyCpuLoad(0.7f, 10);
        var scaledResources = fixture.tester().assertResources("Scaling up since resource usage is too high",
                                                               9, 1, 3.0,  6.8, 288.7,
                                                               fixture.autoscale());

        fixture.deploy(Capacity.from(scaledResources));
        assertEquals("Cluster in flux -> No further change", Autoscaling.Status.waiting, fixture.autoscale().status());

        fixture.deactivateRetired(Capacity.from(scaledResources));

        fixture.loader().applyCpuLoad(0.19f, 10);
        assertEquals("Load change is small -> No change", Optional.empty(), fixture.autoscale().resources());

        fixture.loader().applyCpuLoad(0.1f, 10);
        assertEquals("Too little time passed for downscaling -> No change", Optional.empty(), fixture.autoscale().resources());

        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(0.1f, 10);
        assertTrue("Last scaling not completed", fixture.autoscale().resources().isEmpty());

        fixture.completeLastScaling();
        fixture.tester().clock().advance(Duration.ofDays(7));
        fixture.loader().applyCpuLoad(0.1f, 10);
        fixture.tester().assertResources("Scaling cpu down since usage has gone down significantly",
                                         9, 1, 1.0, 6.5, 243.9,
                                         fixture.autoscale());
    }

    /** Using too many resources for a short period is proof we should scale up regardless of the time that takes. */
    @Test
    public void test_no_autoscaling_with_no_measurements() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        assertTrue(fixture.autoscale().resources().isEmpty());
    }

    @Test
    public void test_no_autoscaling_with_no_measurements_exclusive() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(false).build();
        assertTrue(fixture.autoscale().resources().isEmpty());
    }

    /** Using too many resources for a short period is proof we should scale up regardless of the time that takes. */
    @Test
    public void test_autoscaling_up_is_fast() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.loader().applyLoad(new Load(0.1, 0.1, 0.1, 0, 0), 3);
        fixture.loader().applyLoad(new Load(1.0, 1.0, 1.0, 0, 0), 1);
        fixture.tester().assertResources("Scaling up since resource usage is too high",
                                         8, 1, 5.7, 17.0, 75.1,
                                         fixture.autoscale());
    }

    @Test
    public void test_container_scaling_down_exclusive() {
        var min = new ClusterResources(2, 1, new NodeResources(4, 8, 50, 0.1));
        var now = new ClusterResources(8, 1, new NodeResources(4, 8, 50, 0.1));
        var max = new ClusterResources(8, 1, new NodeResources(4, 8, 50, 0.1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(false)
                                               .clusterType(ClusterSpec.Type.container)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.tester().setScalingDuration(fixture.applicationId(), fixture.clusterSpec.id(), Duration.ofMinutes(5));

        fixture.loader().applyLoad(new Load(0.01, 0.38, 0, 0, 0), 5);
        fixture.tester().assertResources("Scaling down",
                                         2, 1, 4, 8, 50,
                                         fixture.autoscale());
    }

    @Test
    public void test_containers_wont_scale_up_on_memory() {
        var min = new ClusterResources(2, 1, new NodeResources(4, 8, 50, 0.1));
        var now = new ClusterResources(4, 1, new NodeResources(4, 8, 50, 0.1));
        var max = new ClusterResources(8, 1, new NodeResources(4, 8, 50, 0.1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(false)
                                               .clusterType(ClusterSpec.Type.container)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.tester().setScalingDuration(fixture.applicationId(), fixture.clusterSpec.id(), Duration.ofMinutes(5));

        fixture.loader().applyLoad(new Load(0.1875, 1.0, 0.95, 0, 0), 50);
        assertEquals(Autoscaling.Status.insufficient, fixture.autoscale().status());
    }

    @Test
    public void initial_deployment_with_host_sharing_flag() {
        var min = new ClusterResources(7, 1, new NodeResources(2.0, 10.0, 384.0, 0.1));
        var max = new ClusterResources(7, 1, new NodeResources(2.4, 32.0, 768.0, 0.1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(false)
                                               .capacity(Capacity.from(min, max))
                                               .initialResources(Optional.empty())
                                               .hostSharing()
                                               .build();
        fixture.tester().assertResources("Initial resources at min, since flag turns on host sharing",
                                         7, 1, 2.0, 10.0, 384.0,
                                         fixture.currentResources().advertisedResources());
    }

    @Test
    public void initial_deployment_with_host_sharing_flag_and_too_small_min() {
        var min = new ClusterResources(1, 1, new NodeResources(0.5, 4.0, 10, 0.1));
        var max = new ClusterResources(1, 1, new NodeResources(2.0, 8.0, 50, 0.1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsSetup(false, Environment.test)
                                               .clusterType(ClusterSpec.Type.container)
                                               .capacity(Capacity.from(min, max))
                                               .initialResources(Optional.empty())
                                               .hostSharing()
                                               .build();
        fixture.tester().assertResources("Initial resources at min, since flag turns on host sharing",
                                         1, 1, 0.5, 4.0, 10.0,
                                         fixture.currentResources().advertisedResources());
    }

    /** When scaling up, disregard underutilized dimensions (memory here) */
    @Test
    public void test_only_autoscaling_up_quickly() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.setScalingDuration(Duration.ofHours(12)); // Fixture sets last completion to be 1 day into the past
        fixture.loader().applyLoad(new Load(1.0, 0.1, 1.0, 0, 0), 10);
        fixture.tester().assertResources("Scaling up (only) since resource usage is too high",
                                         5, 1, 12.5, 14.9, 131.5,
                                         fixture.autoscale());
    }

    /** When ok to scale down, scale in both directions simultaneously (compare to test_only_autoscaling_up_quickly) */
    @Test
    public void test_scale_in_both_directions_when_ok_to_scale_down() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.setScalingDuration(Duration.ofHours(12)); // Fixture sets last completion to be 1 day into the past
        fixture.tester.clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 0.1, 1.0, 0, 0), 10);
        fixture.tester().assertResources("Scaling cpu and disk up and memory down",
                                         5, 1, 12.5, 4.0, 131.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_scale_in_both_directions_when_ok_to_scale_down_exclusive() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(false).build();
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.tester.clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 0.1, 1.0, 0, 0), 10);
        fixture.tester().assertResources("Scaling cpu and disk up, memory follows",
                                         8, 1, 8, 16.0, 60.7,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_uses_peak() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.loader().applyCpuLoad(0.70, 1);
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.tester().assertResources("Scaling up since peak resource usage is too high",
                                         5, 1, 7.6, 11.9, 50.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_uses_peak_exclusive() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(false).build();
        fixture.setScalingDuration(Duration.ofHours(9));
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.loader().applyCpuLoad(0.70, 1);
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.tester().assertResources("Scaling up since peak resource usage is too high",
                                         5, 1, 8, 16.0, 200,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_uses_peak_preprovisioned() {
        var fixture = DynamicProvisioningTester.fixture().hostCount(15).build();
        fixture.setScalingDuration(Duration.ofHours(9));
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.loader().applyCpuLoad(0.70, 1);
        fixture.loader().applyCpuLoad(0.01, 100);
        fixture.tester().assertResources("Scaling up cpu since peak resource usage is too high",
                                         5, 1, 7.6, 12.8, 60.0,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_without_traffic_exclusive() {
        var min = new ClusterResources(1, 1, new NodeResources(0.5, 4, 100, 0.3));
        var now = new ClusterResources(4, 1, new NodeResources(8, 16, 100, 0.3));
        var max = new ClusterResources(4, 1, new NodeResources(16, 32, 500, 0.3));
        var fixture = DynamicProvisioningTester.fixture(min, now, max)
                                               .clusterType(ClusterSpec.Type.container)
                                               .awsProdSetup(false)
                                               .build();
        var duration = fixture.loader().addMeasurements(new Load(0.04, 0.39, 0.01, 0, 0), 20);
        fixture.tester().clock().advance(duration.negated());
        fixture.loader().zeroTraffic(20, 1);
        fixture.tester().assertResources("Scaled down",
                                         2, 1, 2, 8, 100,
                                         fixture.autoscale());
    }

    /** We prefer fewer nodes for container clusters as (we assume) they all use the same disk and memory */
    @Test
    public void test_autoscaling_single_container_group() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).clusterType(ClusterSpec.Type.container).build();

        fixture.loader().applyCpuLoad(0.25f, 120);
        var scaledResources = fixture.tester().assertResources("Scaling cpu up",
                                                               6, 1, 2,  16.0, 40.8,
                                                               fixture.autoscale());
        fixture.deploy(Capacity.from(scaledResources));
        fixture.deactivateRetired(Capacity.from(scaledResources));
        fixture.completeLastScaling();
        fixture.loader().applyCpuLoad(0.1f, 120);
        fixture.tester().assertResources("Scaling down since cpu usage has gone down",
                                         4, 1, 2, 16, 34.0,
                                         fixture.autoscale());
    }

    @Test
    public void autoscaling_handles_disk_setting_changes_exclusive_preprovisioned() {
        var resources = new NodeResources(3, 100, 100, 1, slow);
        var fixture = DynamicProvisioningTester.fixture()
                                               .hostCount(20)
                                               .hostFlavors(resources)
                                               .initialResources(Optional.of(new ClusterResources(5, 1, resources)))
                                               .capacity(Capacity.from(new ClusterResources(5, 1, resources)))
                                               .build();

        assertTrue(fixture.tester().nodeRepository().nodes().list().owner(fixture.applicationId).stream()
                          .allMatch(n -> n.allocation().get().requestedResources().diskSpeed() == slow));

        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.loader().applyCpuLoad(0.25, 120);

        // Changing min and max from slow to any
        ClusterResources min = new ClusterResources( 2, 1,
                                                     new NodeResources(1, 1, 1, 1, DiskSpeed.any));
        ClusterResources max = new ClusterResources(20, 1,
                                                    new NodeResources(100, 1000, 1000, 1, DiskSpeed.any));
        var capacity = Capacity.from(min, max);
        ClusterResources scaledResources = fixture.tester().assertResources("Scaling up",
                                                                            14, 1, 1.5,  27.2, 81.5,
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
        var fixture = DynamicProvisioningTester.fixture()
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
        assertEquals(DiskSpeed.any, fixture.autoscale(capacity).resources().get().nodeResources().diskSpeed());
    }

    @Test
    public void autoscaling_respects_upper_limit() {
        var min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(5, 1, new NodeResources(1.9, 70, 70, 1));
        var max = new ClusterResources( 6, 1, new NodeResources(2.4, 78, 79, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max)).build();

        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.tester().clock().advance(Duration.ofDays(1));
        fixture.loader().applyLoad(new Load(0.25, 0.95, 0.95, 0, 0), 120);
        fixture.tester().assertResources("Scaling up to limit since resource usage is too high",
                                         6, 1, 2.4,  78.0, 79.0,
                                         fixture.autoscale());
    }

    @Test
    public void autoscaling_respects_lower_limit() {
        var min = new ClusterResources( 4, 1, new NodeResources(1.8, 7.4, 8.5, 1));
        var max = new ClusterResources( 6, 1, new NodeResources(2.4, 78, 79, 1));
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).capacity(Capacity.from(min, max)).build();

        // deploy
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.05f, 0.05f, 0.05f, 0, 0),  120);
        fixture.tester().assertResources("Scaling down to limit since resource usage is low",
                                         4, 1, 1.8,  7.4, 23.4,
                                         fixture.autoscale());
    }

    @Test
    public void autoscaling_with_unspecified_resources_use_defaults_exclusive() {
        var min = new ClusterResources( 2, 1, NodeResources.unspecified());
        var max = new ClusterResources( 6, 1, NodeResources.unspecified());
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(false)
                                               .initialResources(Optional.empty())
                                               .capacity(Capacity.from(min, max))
                                               .build();

        NodeResources defaultResources =
                fixture.tester().nodeRepository().capacityPoliciesFor(fixture.applicationId)
                       .specifyFully(NodeResources.unspecified(), fixture.clusterSpec);

        fixture.tester().assertResources("Min number of nodes and default resources",
                                         2, 1, defaultResources,
                                         fixture.nodes().toResources());
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.25, 0.95, 0.95, 0, 0), 120);
        fixture.tester().assertResources("Scaling up",
                                         5, 1,
                                         defaultResources.vcpu(), defaultResources.memoryGiB(), defaultResources.diskGb(),
                                         fixture.autoscale());
    }

    @Test
    public void autoscaling_respects_group_limit() {
        var min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(5, 5, new NodeResources(3.0, 10, 10, 1));
        var max = new ClusterResources(18, 6, new NodeResources(100, 1000, 1000, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(0.5, 240);
        fixture.tester().assertResources("Scaling cpu up",
                                         6, 6, 4.8,  7.4, 22.3,
                                         fixture.autoscale());
    }

    @Test
    public void autoscaling_respects_group_size_limit() {
        var min = new ClusterResources( 2, 2, new NodeResources(1, 1, 10, 1));
        var now = new ClusterResources(5, 5, new NodeResources(3.0, 10, 100, 1));
        var max = new ClusterResources(18, 6, new NodeResources(100, 1000, 10000, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max, IntRange.of(2, 3), false, true, Optional.empty(), ClusterInfo.empty()))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(0.6, 240);
        fixture.tester().assertResources("Scaling cpu up",
                                         12, 6, 3.2,  4.2, 27.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_limits_when_min_equals_max() {
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).capacity(Capacity.from(min, min)).build();

        fixture.tester().clock().advance(Duration.ofDays(1));
        fixture.loader().applyCpuLoad(0.25, 120);
        assertEquals(Autoscaling.Status.unavailable, fixture.autoscale().status());
    }

    @Test
    public void container_prefers_remote_disk_when_no_local_match_exclusive() {
        var resources = new ClusterResources( 2, 1, new NodeResources(3, 100, 50, 1));
        var local  = new NodeResources(3, 100,  75, 1, fast, StorageType.local);
        var remote = new NodeResources(3, 100,  50, 1, fast, StorageType.remote);
        var fixture = DynamicProvisioningTester.fixture()
                                               .dynamicProvisioning(true)
                                               .allowHostSharing(false)
                                               .clusterType(ClusterSpec.Type.container)
                                               .hostFlavors(local, remote)
                                               .capacity(Capacity.from(resources))
                                               .initialResources(Optional.of(new ClusterResources(3, 1, resources.nodeResources())))
                                               .build();

        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.01, 0.01, 0.01, 0, 0), 120);
        List<Autoscaling> suggestions = fixture.suggest();
        fixture.tester().assertResources("Choosing the remote disk flavor as it has less disk",
                                         2, 1, 3.0,  100.0, 10.0,
                                         suggestions);
        assertEquals("Choosing the remote disk flavor as it has less disk",
                     StorageType.remote, suggestions.stream().findFirst().flatMap(Autoscaling::resources).get().nodeResources().storageType());
    }

    @Test
    public void suggestions_ignores_limits() {
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).capacity(Capacity.from(min, min)).build();
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(1.0, 120);
        fixture.tester().assertResources("Suggesting above capacity limit",
                                         5, 1, 10.9,  11.9, 50.5,
                                         fixture.tester().suggest(fixture.applicationId, fixture.clusterSpec.id(), min, min));
    }

    @Test
    public void suggestions_avoid_duplicate_resources() {
        ClusterResources min = new ClusterResources(2, 1, new NodeResources(1, 1, 1, 1));
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(false).capacity(Capacity.from(min, min)).build();
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(1.0, 120);
        List<Autoscaling> suggestions = fixture.tester().suggest(fixture.applicationId, fixture.clusterSpec.id(), min, min);
        Set<NodeResources> seenResources = new HashSet<>();
        for (var suggestion : suggestions) {
            var nodeResources = suggestion.resources().get().nodeResources();
            if (seenResources.contains(nodeResources))
                fail("Duplicate suggestion with resources " + nodeResources);
            seenResources.add(nodeResources);
        }
    }

    @Test
    public void suggests_max_1_group_in_container_clusters() {
        ClusterResources min = new ClusterResources(2, 1, new NodeResources(1, 1, 1, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .clusterType(ClusterSpec.Type.container)
                                               .awsProdSetup(true).capacity(Capacity.from(min, min)).build();
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(1.0, 120);
        List<Autoscaling> suggestions = fixture.tester().suggest(fixture.applicationId, fixture.clusterSpec.id(), min, min);
        assertTrue(suggestions.stream().allMatch(s -> s.resources().get().groups() == 1));
    }

    @Test
    public void suggestions_ignores_limits_exclusive() {
        ClusterResources min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(false).capacity(Capacity.from(min, min)).build();
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(1.0, 120);
        fixture.tester().assertResources("Suggesting above capacity limit",
                                         7, 1, 8,  16,  200.0,
                                         fixture.tester().suggest(fixture.applicationId, fixture.clusterSpec.id(), min, min));
    }

    @Test
    public void not_using_out_of_service_measurements() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.9, 0.6, 0.7, 0, 0),  1, false, true, 120);
        assertTrue("Not scaling up since nodes were measured while cluster was out of service",
                   fixture.autoscale().resources().isEmpty());
    }

    @Test
    public void not_using_unstable_measurements() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.9, 0.6, 0.7, 0, 0),  1, true, false, 120);
        assertTrue("Not scaling up since nodes were measured while cluster was unstable",
                   fixture.autoscale().resources().isEmpty());
    }

    @Test
    public void too_small_disk_compared_to_memory() {
        var resources = new ClusterResources(2, 1, new NodeResources(1, 10, 19, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(resources))
                                               .build();
        assertEquals(2, fixture.tester().provisionLogger().applicationLog().size()); // tester deploys twice
        assertEquals("WARNING: Requested disk (19.0Gb) in cluster 'cluster1' is not large enough to fit core/heap dumps. Minimum recommended disk resources is 2x memory for containers and 3x memory for content",
                     fixture.tester().provisionLogger().applicationLog().get(0));
    }

    @Test
    public void autoscaling_shouldnt_choose_too_small_disk_compared_to_memory() {
        var min = new ClusterResources(10, 1, new NodeResources(1, 10, 19, 1));
        var now = new ClusterResources(10, 1, new NodeResources(5, 50, 150, 1));
        var max = new ClusterResources(10, 1, new NodeResources(10, 100, 200, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.5, 0.8, 0.1, 0, 0), 120);
        fixture.tester().assertResources("Suggesting resources where disk is 3x memory (this is a content cluster)",
                                         11, 1, 13.9,  60.0,  179.9,
                                         fixture.tester().suggest(fixture.applicationId, fixture.clusterSpec.id(), min, min));
        fixture.tester().assertResources("Autoscaling to resources where disk is 3x memory (this is a content cluster)",
                                         10, 1, 10.0,  66.2,  198.6,
                                         fixture.tester().autoscale(fixture.applicationId, fixture.clusterSpec, Capacity.from(min, max)));
    }

    @Test
    public void autoscaling_shouldnt_choose_too_small_disk_compared_to_memory_exclusive() {
        var min = new ClusterResources(10, 1, new NodeResources(1, 10, 19, 1, DiskSpeed.any, StorageType.remote));
        var now = new ClusterResources(10, 1, new NodeResources(16, 64, 192, 1, DiskSpeed.any, StorageType.remote));
        var max = new ClusterResources(10, 1, new NodeResources(30, 200, 500, 1, DiskSpeed.any, StorageType.remote));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(false)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.5, 0.8, 0.1, 0, 0), 120);
        fixture.tester().assertResources("Suggesting resources where disk is 3x memory (this is a content cluster)",
                                         7, 1, 72.0,  144.0,  1800.0,
                                         fixture.tester().suggest(fixture.applicationId, fixture.clusterSpec.id(), min, min));
        fixture.tester().assertResources("Autoscaling to resources where disk is 3x memory (this is a content cluster)",
                                         10, 1, 16.0,  64,  247.5,
                                         fixture.tester().autoscale(fixture.applicationId, fixture.clusterSpec, Capacity.from(min, max)));
    }

    @Test
    public void test_autoscaling_group_size_unconstrained() {
        var min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(5, 5, new NodeResources(3, 100, 300, 1));
        var max = new ClusterResources(20, 20, new NodeResources(10, 1000, 1000, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyCpuLoad(0.9, 120);
        fixture.tester().assertResources("Scaling up to 2 nodes, scaling memory and disk down at the same time",
                                         8, 4, 7.9,  41.5, 124.6,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_group_size_1() {
        var min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(5, 5, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(20, 20, new NodeResources(10, 1000, 1000, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max, IntRange.of(1), false, true, Optional.empty(), ClusterInfo.empty()))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.setScalingDuration(Duration.ofHours(6));
        fixture.loader().applyCpuLoad(0.9, 120);
        fixture.tester().assertResources("Scaling up to 2 nodes, scaling memory and disk down at the same time",
                                         8, 8, 8.6,  78.6, 235.8,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_groupsize_by_cpu_read_dominated() {
        var min = new ClusterResources( 3, 1, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources( 6, 2, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(21, 7, new NodeResources(100, 1000, 1000, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        Duration timePassed = fixture.loader().addCpuMeasurements(0.25, 120);
        fixture.tester().clock().advance(timePassed.negated());
        fixture.loader().addLoadMeasurements(10, t -> t == 0 ? 200.0 : 100.0, t -> 10.0);
        fixture.tester().assertResources("Changing to 1 group is cheaper",
                                         7, 1, 2.7, 43.3, 129.8,
                                         fixture.autoscale());
    }

    /** Same as above but mostly write traffic, which favors smaller groups */
    @Test
    public void test_autoscaling_groupsize_by_cpu_write_dominated() {
        var min = new ClusterResources( 3, 1, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(6, 2, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(21, 7, new NodeResources(100, 1000, 1000, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        Duration timePassed = fixture.loader().addCpuMeasurements(0.25, 120);
        fixture.tester().clock().advance(timePassed.negated());
        fixture.loader().addLoadMeasurements(10, t -> t == 0 ? 20.0 : 10.0, t -> 100.0);
        fixture.tester().assertResources("Scaling down since resource usage is too high, changing to 1 group is cheaper",
                                         5, 1, 1.0,  62.6, 187.7,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_groupsize() {
        var min = new ClusterResources( 2, 2, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(6, 2, new NodeResources(10, 100, 100, 1));
        var max = new ClusterResources(30, 30, new NodeResources(100, 100, 1000, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(1));
        fixture.loader().applyMemLoad(1.0, 1000);
        fixture.tester().assertResources("Increase group size to reduce memory load",
                                         8, 2, 14.0,  96.3, 288.8,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_memory_down() {
        var min = new ClusterResources( 2, 1, new NodeResources(1, 1, 1, 1));
        var now = new ClusterResources(6, 1, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(now))
                                               .capacity(Capacity.from(min, max))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.16,  0.02, 0.5, 0, 0), 120);
        fixture.tester().assertResources("Scaling down memory",
                                         6, 1, 2.3, 4.0, 96.2,
                                         fixture.autoscale());
    }

    @Test
    public void scaling_down_only_after_delay() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.loader().applyCpuLoad(0.02, 5);
        assertTrue("Too soon  after initial deployment", fixture.autoscale().resources().isEmpty());
        fixture.tester().clock().advance(Duration.ofHours(12 * 3 + 1));
        fixture.loader().applyCpuLoad(0.02, 5);
        fixture.tester().assertResources("Scaling down since enough time has passed",
                                         5, 1, 1.0, 11.9, 50.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_considers_read_share() {
        var min = new ClusterResources( 1, 1, new NodeResources(3, 100, 100, 1));
        var max = new ClusterResources(10, 1, new NodeResources(3, 100, 100, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .capacity(Capacity.from(min, max))
                                               .build();

        fixture.tester.clock().advance(Duration.ofDays(1));
        fixture.loader().applyCpuLoad(0.5, 120);
        // (no read share stored)
        fixture.tester().assertResources("Advice to scale up since we set aside for bcp by default",
                                         7, 1, 3,  100, 100,
                                         fixture.autoscale());

        fixture.loader().applyCpuLoad(0.5, 120);
        fixture.storeReadShare(0.25, 0.5);
        fixture.tester().assertResources("Half of global share is the same as the default assumption used above",
                                         7, 1, 3,  100, 100,
                                         fixture.autoscale());

        fixture.tester.clock().advance(Duration.ofDays(1));
        fixture.loader().applyCpuLoad(0.5, 120);
        fixture.storeReadShare(0.5, 0.5);
        fixture.tester().assertResources("Advice to scale down since we don't need room for bcp",
                                         4, 1, 3,  100, 100,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_considers_growth_rate() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.setScalingDuration(Duration.ofHours(6));

        fixture.tester().clock().advance(Duration.ofDays(2));
        Duration timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 200.0 : 100.0, t -> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.25, 200);
        fixture.tester().assertResources("Scale up since we assume we need 2x cpu for growth when no scaling time data",
                                         5, 1, 2.7,  11.9, 50.5,
                                         fixture.autoscale());

        fixture.setScalingDuration(Duration.ofHours(8));
        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t -> 100.0 + (t < 50 ? t : 100 - t), t -> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.15, 200);
        fixture.tester().assertResources("Scale down since observed growth is slower than scaling time",
                                         5, 1, 1.3,   11.9, 50.5,
                                         fixture.autoscale());

        fixture.setScalingDuration(Duration.ofHours(8));
        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100,
                                                         t -> 100.0 + (t < 50 ? t * t * t : 125000 - (t - 49) * (t - 49) * (t - 49)),
                                                         t -> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.25, 200);
        fixture.tester().assertResources("Scale up since observed growth is faster than scaling time",
                                         5, 1, 2.6,  11.9, 50.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_weights_growth_rate_by_confidence() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.setScalingDuration(Duration.ofHours(6));

        double scalingFactor = 1.0/6000; // To make the average query rate low
        fixture.tester().clock().advance(Duration.ofDays(2));
        Duration timeAdded = fixture.loader().addLoadMeasurements(100,
                                                         t -> scalingFactor * (100.0 + (t < 50 ? t * t * t : 155000 - (t - 49) * (t - 49) * (t - 49))),
                                                         t -> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.7, 200);
        fixture.tester().assertResources("Scale up slightly since observed growth is faster than scaling time, but we are not confident",
                                         5, 1, 2.4,  11.9, 50.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_considers_query_vs_write_rate() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();
        fixture.setScalingDuration(Duration.ofHours(6));

        fixture.loader().addCpuMeasurements(0.4, 220);

        // Why twice the query rate at time = 0?
        // This makes headroom for queries doubling, which we want to observe the effect of here

        fixture.tester().clock().advance(Duration.ofDays(2));
        var timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 200.0 : 100.0, t -> 100.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        fixture.tester.assertResources("Query and write load is equal -> scale up somewhat",
                                       5, 1, 3.1,   11.9, 50.5,
                                       fixture.autoscale());

        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 800.0 : 400.0, t -> 100.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        // TODO: Ackhually, we scale up less here - why?
        fixture.tester().assertResources("Query load is 4x write load -> scale up more",
                                         5, 1, 2.4,  11.9, 50.5,
                                         fixture.autoscale());

        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 200.0 : 100.0, t -> 1000.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        fixture.tester().assertResources("Write load is 10x query load -> scale down",
                                         5, 1, 1.3,  11.9, 50.5,
                                         fixture.autoscale());

        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t -> t == 0 ? 200.0 : 100.0, t-> 0.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        fixture.tester().assertResources("Query only -> larger",
                                         5, 1, 3.8,  11.9, 50.5,
                                         fixture.autoscale());

        fixture.tester().clock().advance(Duration.ofDays(2));
        timeAdded = fixture.loader().addLoadMeasurements(100, t ->  0.0, t -> 100.0);
        fixture.tester.clock().advance(timeAdded.negated());
        fixture.loader().addCpuMeasurements(0.4, 200);
        fixture.tester().assertResources("Write only -> smallest possible",
                                         5, 1, 1.0,  11.9, 50.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_in_dev_preprovisioned() {
        var fixture = DynamicProvisioningTester.fixture()
                                               .hostCount(5)
                                               .zone(new Zone(Environment.dev, RegionName.from("us-east")))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 1.0, 1.0, 0, 0), 200);
        assertTrue("Not attempting to scale up because policies dictate we'll only get one node",
                   fixture.autoscale().resources().isEmpty());
    }

    @Test
    public void test_autoscaling_in_dev_with_cluster_size_constraint() {
        var min = new ClusterResources(4, 1,
                                       new NodeResources(1, 4, 10, 1, NodeResources.DiskSpeed.any));
        var max = new ClusterResources(20, 20,
                                       new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsSetup(true, Environment.dev)
                                               .capacity(Capacity.from(min, max, IntRange.of(3, 5), false, true, Optional.empty(), ClusterInfo.empty()))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 1.0, 1.0, 0, 0), 200);
        assertEquals("Don't autoscale: Autoscaling is disabled in single node clusters", fixture.autoscale().toString());
    }

    /** Same setup as test_autoscaling_in_dev(), just with required = true */
    @Test
    public void test_autoscaling_in_dev_with_required_resources_preprovisioned() {
        var requiredCapacity =
                Capacity.from(new ClusterResources(2, 1,
                                                   new NodeResources(1, 1, 1, 1, NodeResources.DiskSpeed.any)),
                              new ClusterResources(20, 1,
                                                   new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any)),
                              IntRange.empty(),
                              true,
                              true,
                              Optional.empty(),
                              ClusterInfo.empty());

        var fixture = DynamicProvisioningTester.fixture()
                                               .hostCount(5)
                                               .capacity(requiredCapacity)
                                               .zone(new Zone(Environment.dev, RegionName.from("us-east")))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 1.0, 1.0, 0, 0), 200);
        fixture.tester().assertResources("We scale even in dev because resources are 'required'",
                                         3, 1, 1.0,  13.4, 62.5,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_in_dev_with_required_unspecified_resources_preprovisioned() {
        var requiredCapacity =
                Capacity.from(new ClusterResources(1, 1, NodeResources.unspecified()),
                              new ClusterResources(3, 1, NodeResources.unspecified()),
                              IntRange.empty(),
                              true,
                              true,
                              Optional.empty(),
                              ClusterInfo.empty());

        var fixture = DynamicProvisioningTester.fixture()
                                               .hostCount(5)
                                               .capacity(requiredCapacity)
                                               .zone(new Zone(Environment.dev, RegionName.from("us-east")))
                                               .build();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(1.0, 1.0, 1.0, 0, 0), 200);
        fixture.tester().assertResources("We scale even in dev because resources are required",
                                         3, 1, 1.5,  8, 50,
                                         fixture.autoscale());
    }

    @Test
    public void test_scaling_down_leaves_too_little_headroom() {
        var r = new NodeResources(16, 32, 100, 1, NodeResources.DiskSpeed.any);
        var min = new ClusterResources( 3, 3, r);
        var now = new ClusterResources( 4, 4, r);
        var max = new ClusterResources( 5, 5, r);
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(false)
                                               .capacity(Capacity.from(min, max, IntRange.from(1)))
                                               .clusterType(ClusterSpec.Type.content)
                                               .initialResources(Optional.of(now))
                                               .build();
        fixture.loader().applyCpuLoad(0.17, 10);
        assertTrue("Not scaling down as that would leave just 4.5% headroom before needing to scale up again",
                   fixture.autoscale().resources().isEmpty());
    }

    @Test
    public void test_changing_exclusivity() {
        var min = new ClusterResources( 2, 1, new NodeResources(  3,    8,  100, 1));
        var max = new ClusterResources(20, 1, new NodeResources(100, 1000, 1000, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .cluster(clusterSpec(true))
                                               .capacity(Capacity.from(min, max))
                                               .initialResources(Optional.empty())
                                               .build();
        fixture.tester().assertResources("Initial deployment at minimum",
                                         2, 1, 4, 8, 100,
                                         fixture.currentResources().advertisedResources());

        fixture.tester().deploy(fixture.applicationId(), clusterSpec(false), fixture.capacity());
        fixture.loader().applyLoad(new Load(0.1, 0.1, 0.1, 0, 0), 5);
        fixture.tester().assertResources("Exclusive nodes makes no difference here",
                                         2, 1, 4, 8, 100.0,
                                         fixture.autoscale());

        fixture.tester().deploy(fixture.applicationId(), clusterSpec(true), fixture.capacity());
        fixture.loader().applyLoad(new Load(0.1, 0.1, 0.1, 0, 0), 5);
        fixture.tester().assertResources("Reverts to the initial resources",
                                         2, 1, 4, 8, 100,
                                         fixture.currentResources().advertisedResources());
    }

    /** Tests an autoscaling scenario which should cause in-place resize. */
    @Test
    public void test_resize() {
        var min = new ClusterResources(7, 1, new NodeResources(  2, 10, 384, 1));
        var now = new ClusterResources(7, 1, new NodeResources(  3.4, 16.2, 450.1, 1));
        var max = new ClusterResources(7, 1, new NodeResources(  4, 32, 768, 1));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .capacity(Capacity.from(min, max))
                                               .initialResources(Optional.of(now))
                                               .build();
        var initialNodes = fixture.nodes().asList();
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.loader().applyLoad(new Load(0.06, 0.52, 0.27, 0, 0), 100);
        var autoscaling = fixture.autoscale();
        fixture.tester().assertResources("Scaling down",
                                         7, 1, 2, 15.8, 384.0,
                                         autoscaling);
        fixture.deploy(Capacity.from(autoscaling.resources().get()));
        assertEquals("Initial nodes are kept", initialNodes, fixture.nodes().asList());
    }

    private ClusterSpec clusterSpec(boolean exclusive) {
        return ClusterSpec.request(ClusterSpec.Type.content,
                                   ClusterSpec.Id.from("test")).vespaVersion("8.1.2")
                          .exclusive(exclusive)
                          .build();
    }

    @Test
    public void change_not_requiring_node_replacement_is_preferred() {
        var min = new ClusterResources(5, 1, new NodeResources(  16, 64, 200, 1, DiskSpeed.fast, StorageType.remote));
        var max = new ClusterResources(6, 1, new NodeResources(  16, 64, 200, 1, DiskSpeed.fast, StorageType.remote));

        List<Flavor> flavors = List.of(new Flavor("arm_16", new NodeResources(  16, 64, 200, 1, DiskSpeed.fast, StorageType.remote, NodeResources.Architecture.arm64)),
                                       new Flavor("x86_16", new NodeResources(  16, 64, 200, 1, DiskSpeed.fast, StorageType.remote, NodeResources.Architecture.x86_64)));
        var fixture = DynamicProvisioningTester.fixture()
                                               .clusterType(ClusterSpec.Type.container)
                                               .hostFlavors(flavors)
                                               .awsZone(false, Environment.prod)
                                               .capacity(Capacity.from(min, max))
                                               .initialResources(Optional.of(min.with(min.nodeResources().with(NodeResources.Architecture.x86_64))))
                                               .build();
        var nodes = fixture.nodes().not().retired().asList();
        assertEquals(5, nodes.size());
        assertEquals(NodeResources.Architecture.x86_64, nodes.get(0).resources().architecture());

        fixture.tester().clock().advance(Duration.ofHours(5));
        fixture.loader().applyCpuLoad(0.27, 10); // trigger rescaling, but don't cause fulfilment < 1
        var autoscaling = fixture.autoscale();
        fixture.deploy(Capacity.from(autoscaling.resources().get()));
        nodes = fixture.nodes().not().retired().asList();
        assertEquals(6, nodes.size());
        assertEquals("We stay with x86 even though the first matching flavor is arm",
                     NodeResources.Architecture.x86_64, nodes.get(0).resources().architecture());
    }

}

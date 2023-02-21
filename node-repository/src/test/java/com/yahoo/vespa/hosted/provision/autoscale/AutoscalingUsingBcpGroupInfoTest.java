package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.applications.BcpGroupInfo;
import com.yahoo.vespa.hosted.provision.provisioning.DynamicProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;

/**
 * Tests autoscaling using information from the BCP group this cluster deployment
 * is part of to supplement local data when the local deployment lacks sufficient traffic.
 *
 * @author bratseth
 */
public class AutoscalingUsingBcpGroupInfoTest {

    /** Tests with varying BCP group info parameters. */
    @Test
    public void test_autoscaling_single_content_group() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();

        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(100, 1.1, 0.3));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 3.6,  6.1, 25.3,
                                         fixture.autoscale());

        // Higher query rate
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200, 1.1, 0.3));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 7.1,  6.1, 25.3,
                                         fixture.autoscale());

        // Higher headroom
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(100, 1.3, 0.3));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 4.2,  6.1, 25.3,
                                         fixture.autoscale());

        // Higher per query cost
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(100, 1.1, 0.45));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 5.4,  6.1, 25.3,
                                         fixture.autoscale());

        // Bcp elsewhere is 0 - use local only
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(0, 1.1, 0.45));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling using local info",
                                         8, 1, 1,  7.0, 29.0,
                                         fixture.autoscale());
    }

    /** Tests with varying BCP group info parameters. */
    @Test
    public void test_autoscaling_multiple_content_groups() {
        var min = new ClusterResources(3, 3,
                                       new NodeResources(1, 4, 10, 1, NodeResources.DiskSpeed.any));
        var max = new ClusterResources(21, 3,
                                       new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any));
        var fixture = DynamicProvisioningTester.fixture()
                                               .awsProdSetup(true)
                                               .initialResources(Optional.of(new ClusterResources(9, 3, new NodeResources(2, 16, 75, 1))))
                                               .capacity(Capacity.from(min, max))
                                               .build();

        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(100, 1.1, 0.3));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         3, 3, 10.5,  41.0, 168.9,
                                         fixture.autoscale());

        // Higher query rate
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200, 1.1, 0.3));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         3, 3, 20.9,  41.0, 168.9,
                                         fixture.autoscale());

        // Higher headroom
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(100, 1.3, 0.3));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         3, 3, 12.4,  41.0, 168.9,
                                         fixture.autoscale());

        // Higher per query cost
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(100, 1.1, 0.45));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         3, 3, 15.7,  41.0, 168.9,
                                         fixture.autoscale());
    }

    /**
     * Tests with varying BCP group info parameters for containers.
     * Differences from content
     * - No host sharing.
     * - Memory and disk is independent of cluster size.
     */
    @Test
    public void test_autoscaling_container() {
        var fixture = DynamicProvisioningTester.fixture().clusterType(ClusterSpec.Type.container).awsProdSetup(true).build();

        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(100, 1.1, 0.3));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         8, 1, 4.0,  16.0, 40.8,
                                         fixture.autoscale());

        // Higher query rate (mem and disk changes are due to being assigned larger hosts where we get less overhead share
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200, 1.1, 0.3));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         8, 1, 8.0,  16.0, 40.8,
                                         fixture.autoscale());

        // Higher headroom
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(100, 1.3, 0.3));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         5, 1, 8.0,  16.0, 40.8,
                                         fixture.autoscale());

        // Higher per query cost
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(100, 1.1, 0.45));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         6, 1, 8.0,  16.0, 40.8,
                                         fixture.autoscale());
    }

    @Test
    public void test_autoscaling_single_content_group_with_some_local_traffic() {
        var fixture = DynamicProvisioningTester.fixture().awsProdSetup(true).build();

        // Baseline: No local traffic, group traffic indicates much higher cpu usage than local
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200, 1.3, 0.45));
        fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         8, 1, 14.2,  7.0, 29.0,
                                         fixture.autoscale());

        // Some local traffic
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200, 1.3, 0.45));
        Duration duration1 = fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().clock().advance(duration1.negated());
        fixture.loader().addQueryRateMeasurements(10, __ -> 10.0);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         8, 1, 6.9,  7.0, 29.0,
                                         fixture.autoscale());

        // Enough local traffic to get half the votes
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200, 1.3, 0.45));
        Duration duration2 = fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().clock().advance(duration2.negated());
        fixture.loader().addQueryRateMeasurements(10, __ -> 50.0);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 2.7,  6.1, 25.3,
                                         fixture.autoscale());

        // Mostly local
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200, 1.3, 0.45));
        Duration duration3 = fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().clock().advance(duration3.negated());
        fixture.loader().addQueryRateMeasurements(10, __ -> 90.0);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 2.1,  6.1, 25.3,
                                         fixture.autoscale());

        // Local only
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200, 1.3, 0.45));
        Duration duration4 = fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().clock().advance(duration4.negated());
        fixture.loader().addQueryRateMeasurements(10, __ -> 100.0);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 2.0,  6.1, 25.3,
                                         fixture.autoscale());

        // No group info, should be the same as the above
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(BcpGroupInfo.empty());
        Duration duration5 = fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().clock().advance(duration5.negated());
        fixture.loader().addQueryRateMeasurements(10, __ -> 100.0);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 2.0,  6.1, 25.3,
                                         fixture.autoscale());

        // 40 query rate, no group info (for reference to the below)
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(BcpGroupInfo.empty());
        Duration duration6 = fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().clock().advance(duration6.negated());
        fixture.loader().addQueryRateMeasurements(10, __ -> 40.0);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 1.4,  6.1, 25.3,
                                         fixture.autoscale());

        // Local query rate is too low but global is even lower so disregard it, giving the same as above
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200/40.0, 1.3, 0.45*40.0));
        Duration duration7 = fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().clock().advance(duration7.negated());
        fixture.loader().addQueryRateMeasurements(10, __ -> 40.0);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 1.4,  6.1, 25.3,
                                         fixture.autoscale());

        // Local query rate is too low to be fully confident, and so is global but as it is slightly larger, incorporate it slightly
        fixture.tester().clock().advance(Duration.ofDays(2));
        fixture.store(new BcpGroupInfo(200/4.0, 1.3, 0.45*4.0));
        Duration duration8 = fixture.loader().addCpuMeasurements(0.7f, 10);
        fixture.tester().clock().advance(duration8.negated());
        fixture.loader().addQueryRateMeasurements(10, __ -> 40.0);
        fixture.tester().assertResources("Scaling up cpu using bcp group cpu info",
                                         9, 1, 1.8,  6.1, 25.3,
                                         fixture.autoscale());
    }

}

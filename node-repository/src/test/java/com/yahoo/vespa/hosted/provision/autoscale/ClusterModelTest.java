// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.Status;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ClusterModelTest {

    private static final double delta = 0.001;

    @Test
    public void unit_adjustment_should_cause_no_change() {
        var model = clusterModelWithNoData(); // 5 nodes, 1 group
        assertEquals(Load.one(), model.loadAdjustment());
        var target = model.loadAdjustment().scaled(nodeResources());
        int testingNodes = 5 - 1;
        int currentNodes = 5 - 1;
        assertEquals(nodeResources(), model.loadWith(testingNodes, 1).scaled(Load.one().divide(model.loadWith(currentNodes, 1)).scaled(target)));
    }

    @Test
    public void test_traffic_headroom() {
        // No current traffic share: Ideal load is low but capped
        var model1 = clusterModel(new Status(0.0, 1.0),
                                  t -> t == 0 ? 10000.0 : 100.0, t -> 0.0);
        assertEquals(0.30612, model1.idealLoad().cpu(), delta);

        // Almost no current traffic share: Ideal load is low but capped
        var model2 = clusterModel(new Status(0.0001, 1.0),
                                  t -> t == 0 ? 10000.0 : 100.0, t -> 0.0);
        assertEquals(0.30612, model2.idealLoad().cpu(), delta);

        // Almost no traffic: Headroom impact is reduced due to uncertainty
        var model3 = clusterModel(new Status(0.0001, 1.0),
                                  t -> t == 0 ? 10000.0 : 1.0, t -> 0.0);
        assertEquals(0.606183, model3.idealLoad().cpu(), delta);
    }

    @Test
    public void test_growth_headroom() {
        // No traffic data: Ideal load assumes 2 regions
        var model1 = clusterModel(new Status(0.0, 0.0),
                                  t -> t == 0 ? 10000.0 : 100.0, t -> 0.0);
        assertEquals(0.15306, model1.idealLoad().cpu(), delta);

        // No traffic: Ideal load is higher since we now know there is only one zone
        var model2 = clusterModel(new Status(0.0, 1.0),
                                  t -> t == 0 ? 10000.0 : 100.0, t -> 0.0);
        assertEquals(0.30612, model2.idealLoad().cpu(), delta);

        // Almost no current traffic: Similar number as above
        var model3 = clusterModel(new Status(0.0001, 1.0),
                                  t -> t == 0 ? 10000.0 : 100.0, t -> 0.0);
        assertEquals(0.30612, model3.idealLoad().cpu(), delta);

        // Low query rate: Impact of growth headroom is reduced due to uncertainty
        var model4 = clusterModel(new Status(0.0001, 1.0),
                                  t -> t == 0 ? 100.0 : 1.0, t -> 0.0);
        assertEquals(0.60618, model4.idealLoad().cpu(), delta);
    }

    private ClusterModel clusterModelWithNoData() {
        return clusterModel(new Status(0.0, 1.0), t -> 0.0, t -> 0.0);
    }

    private ClusterModel clusterModel(Status status, IntFunction<Double> queryRate, IntFunction<Double> writeRate) {
        ManualClock clock = new ManualClock();
        Application application = Application.empty(ApplicationId.from("t1", "a1", "i1"));
        ClusterSpec clusterSpec = clusterSpec();
        Cluster cluster = cluster();
        application = application.with(cluster);
        var nodeRepository = new ProvisioningTester.Builder().build().nodeRepository();
        return new ClusterModel(nodeRepository,
                                application.with(status),
                                clusterSpec, cluster,
                                new AllocatableResources(clusterResources(), clusterSpec, nodeRepository, cluster.cloudAccount().orElse(CloudAccount.empty)),
                                clock, Duration.ofMinutes(10), Duration.ofMinutes(5),
                                timeseries(cluster,100, queryRate, writeRate, clock),
                                ClusterNodesTimeseries.empty());
    }

    private ClusterResources clusterResources() {
        return new ClusterResources(5, 1, nodeResources());
    }

    private NodeResources nodeResources() {
        return new NodeResources(1, 10, 100, 1);
    }

    private ClusterSpec clusterSpec() {
        return ClusterSpec.specification(ClusterSpec.Type.content, ClusterSpec.Id.from("test"))
                          .group(ClusterSpec.Group.from(0))
                          .vespaVersion("7.1.1")
                          .build();
    }

    private Cluster cluster() {
        return Cluster.create(ClusterSpec.Id.from("test"),
                              false,
                              Capacity.from(clusterResources()));
    }

    /** Creates the given number of measurements, spaced 5 minutes between, using the given function */
    private ClusterTimeseries timeseries(Cluster cluster,
                                         int measurements,
                                         IntFunction<Double> queryRate,
                                         IntFunction<Double> writeRate,
                                         ManualClock clock) {
        List<ClusterMetricSnapshot> snapshots = new ArrayList<>(measurements);
        for (int i = 0; i < measurements; i++) {
            snapshots.add(new ClusterMetricSnapshot(clock.instant(), queryRate.apply(i), writeRate.apply(i)));
            clock.advance(Duration.ofMinutes(5));
        }
        return new ClusterTimeseries(cluster.id(),snapshots);
    }

}

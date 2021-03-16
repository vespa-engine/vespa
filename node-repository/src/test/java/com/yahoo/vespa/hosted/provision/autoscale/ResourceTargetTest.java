// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.Status;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ResourceTargetTest {

    private static final double delta = 0.001;

    @Test
    public void test_traffic_headroom() {
        Application application = Application.empty(ApplicationId.from("t1", "a1", "i1"));
        Cluster cluster = new Cluster(ClusterSpec.Id.from("test"),
                                      false,
                                      new ClusterResources(5, 1, new NodeResources(1, 10, 100, 1)),
                                      new ClusterResources(5, 1, new NodeResources(1, 10, 100, 1)),
                                      Optional.empty(),
                                      Optional.empty(),
                                      List.of(),
                                      "");
        application = application.with(cluster);

        // No current traffic: Ideal load is low but capped
        application = application.with(new Status(0.0, 1.0));
        assertEquals(0.131,
                     ResourceTarget.idealCpuLoad(Duration.ofMinutes(10),
                                                 new ClusterTimeseries(cluster.id(),
                                                                       loadSnapshots(100, t -> t == 0 ? 10000.0 : 0.0, t -> 0.0)),
                                                 application),
                     delta);

        // Almost current traffic: Ideal load is low but capped
        application = application.with(new Status(0.0001, 1.0));
        assertEquals(0.131,
                     ResourceTarget.idealCpuLoad(Duration.ofMinutes(10),
                                                 new ClusterTimeseries(cluster.id(),
                                                                       loadSnapshots(100, t -> t == 0 ? 10000.0 : 0.0, t -> 0.0)),
                                                 application),
                     delta);
    }


    /** Creates the given number of measurements, spaced 5 minutes between, using the given function */
    private List<ClusterMetricSnapshot> loadSnapshots(int measurements,
                                                      IntFunction<Double> queryRate,
                                                      IntFunction<Double> writeRate) {
        List<ClusterMetricSnapshot> snapshots = new ArrayList<>(measurements);
        ManualClock clock = new ManualClock();
        for (int i = 0; i < measurements; i++) {
            snapshots.add(new ClusterMetricSnapshot(clock.instant(), queryRate.apply(i), writeRate.apply(i)));
            clock.advance(Duration.ofMinutes(5));
        }
        return snapshots;
    }

}

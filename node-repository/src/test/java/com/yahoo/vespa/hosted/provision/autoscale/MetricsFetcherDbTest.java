// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class MetricsFetcherDbTest {

    @Test
    public void testNodeMetricsDb() {
        ProvisioningTester tester = new ProvisioningTester.Builder().build();
        tester.makeReadyHosts(10, new NodeResources(10, 100, 1000, 10))
              .activateTenantHosts();
        ApplicationId app1 = ProvisioningTester.makeApplicationId("app1");
        var hosts =
                tester.activate(app1,
                                ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("7.0").build(),
                                Capacity.from(new ClusterResources(2, 1, new NodeResources(1, 4, 10, 1))));
        String node0 = hosts.iterator().next().hostname();

        ManualClock clock = tester.clock();
        NodeMetricsDb db = new NodeMetricsDb(tester.nodeRepository());
        List<MetricsFetcher.NodeMetrics> values = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            values.add(new MetricsFetcher.NodeMetrics(node0,
                                                      clock.instant().getEpochSecond(),
                                                      0.9f, 0.6f, 0.6f, 0));
            clock.advance(Duration.ofMinutes(10));
        }
        db.add(values);

        // Avoid off-by-one bug when the below windows starts exactly on one of the above getEpochSecond() timestamps.
        clock.advance(Duration.ofMinutes(1));

        assertEquals(35, measurementCount(db.getNodeTimeseries(clock.instant().minus(Duration.ofHours(6)), List.of(node0))));
        db.gc(clock);
        assertEquals( 5, measurementCount(db.getNodeTimeseries(clock.instant().minus(Duration.ofHours(6)), List.of(node0))));
    }

    private int measurementCount(List<NodeTimeseries> measurements) {
        return measurements.stream().mapToInt(m -> m.size()).sum();
    }

}

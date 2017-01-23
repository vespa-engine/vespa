// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.monitoring;

import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

/**
 * @author oyving
 */
public class ProvisionMetricsTest {

    @Test(timeout = 10_000L)
    public void test_registered_metric() throws InterruptedException {
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
        Curator curator = new MockCurator();
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, Clock.systemUTC(), Zone.defaultZone(),
                new MockNameResolver().mockAnyLookup());
        Node node = nodeRepository.createNode("openStackId", "hostname", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant);
        nodeRepository.addNodes(Collections.singletonList(node));
        Node hostNode = nodeRepository.createNode("openStackId2", "parent", Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host);
        nodeRepository.addNodes(Collections.singletonList(hostNode));

        Map<String, Number> expectedMetrics = new HashMap<>();
        expectedMetrics.put("hostedVespa.provisionedHosts", 1);
        expectedMetrics.put("hostedVespa.parkedHosts", 0);
        expectedMetrics.put("hostedVespa.readyHosts", 0);
        expectedMetrics.put("hostedVespa.reservedHosts", 0);
        expectedMetrics.put("hostedVespa.activeHosts", 0);
        expectedMetrics.put("hostedVespa.inactiveHosts", 0);
        expectedMetrics.put("hostedVespa.dirtyHosts", 0);
        expectedMetrics.put("hostedVespa.failedHosts", 0);

        TestMetric metric = new TestMetric(expectedMetrics.size());
        ProvisionMetrics provisionMetrics = new ProvisionMetrics(metric, nodeRepository);

        metric.latch.await();
        assertEquals(expectedMetrics, metric.values);

        provisionMetrics.deconstruct();
    }

    private static class TestMetric implements Metric {
        public CountDownLatch latch;
        public Map<String, Number> values = new HashMap<>();
        public Map<String, Context> context = new HashMap<>();

        public TestMetric(int latchNumber) {
            this.latch = new CountDownLatch(latchNumber);
        }

        @Override
        public void set(String key, Number val, Context ctx) {
            values.put(key, val);
            context.put(key, ctx);
            countDownAboveZero();
        }

        @Override
        public void add(String key, Number val, Context ctx) {
            values.put(key, val);
            context.put(key, ctx);
            countDownAboveZero();
        }

        @Override
        public Context createContext(Map<String, ?> properties) {
            return null;
        }

        private void countDownAboveZero() {
            if (latch.getCount() == 0) {
                throw new AssertionError("Countdown latch too low - check metric.set metric.add calls");
            }

            latch.countDown();
        }
    }

}

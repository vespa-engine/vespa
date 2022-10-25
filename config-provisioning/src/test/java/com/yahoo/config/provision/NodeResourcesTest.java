// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class NodeResourcesTest {

    @Test
    public void testCost() {
        assertEquals(5.408, new NodeResources(32, 128, 1200, 1).cost(), 0.0001);
    }

    @Test
    void testToString() {
        assertEquals("[vcpu: 1.0, memory: 10.0 Gb, disk 100.0 Gb, architecture: x86_64]",
                               new NodeResources(1., 10., 100., 0).toString());
        assertEquals("[vcpu: 0.3, memory: 3.3 Gb, disk 33.3 Gb, bandwidth: 0.3 Gbps, architecture: x86_64]",
                               new NodeResources(1 / 3., 10 / 3., 100 / 3., 0.3).toString());
        assertEquals("[vcpu: 0.7, memory: 9.0 Gb, disk 66.7 Gb, bandwidth: 0.7 Gbps, architecture: x86_64]",
                               new NodeResources(2 / 3., 8.97, 200 / 3., 0.67).toString());
    }

    @Test
    void testInvalid() {
        assertInvalid("vcpu",      () -> new NodeResources(Double.NaN, 1.0, 1.0, 1.0));
        assertInvalid("memory",    () -> new NodeResources(1.0, Double.NaN, 1.0, 1.0));
        assertInvalid("disk",      () -> new NodeResources(1.0, 1.0, Double.NaN, 1.0));
        assertInvalid("bandwidth", () -> new NodeResources(1.0, 1.0, 1.0, Double.NaN));
    }

    @Test
    void benchmark() {
        NodeResources [] resouces = new NodeResources[100];
        for (int i = 0; i < resouces.length; i++) {
            resouces[i] = new NodeResources(1 / 3., 10 / 3., 100 / 3., 0.3);
        }
        int NUM_ITER = 100; // Use at least 100000 for proper benchmarking
        long warmup = runTest(resouces, NUM_ITER);
        long start = System.nanoTime();
        long benchmark = runTest(resouces, NUM_ITER);
        long duration = System.nanoTime() - start;
        System.out.println("NodeResources.toString() took " + duration / 1000000 + " ms");
        assertEquals(warmup, benchmark);
    }

    private void assertInvalid(String valueName, Supplier<NodeResources> nodeResources) {
        try {
            nodeResources.get();
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals(valueName + " cannot be NaN", e.getMessage());
        }
    }

    private long runTest(NodeResources [] resouces, int num) {
        long sum = 0;
        for (int i = 0; i < num; i++) {
            for (NodeResources ns :resouces) {
                sum += ns.toString().length();
            }
        }
        return sum;
    }

}

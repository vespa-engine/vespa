// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class NodeResourcesTest {

    @Test
    public void testToString() {
        assertEquals("[vcpu: 1.0, memory: 10.0 Gb, disk 100.0 Gb]",
                     new NodeResources(1., 10., 100., 0).toString());
        assertEquals("[vcpu: 0.3, memory: 3.3 Gb, disk 33.3 Gb, bandwidth: 0.3 Gbps]",
                     new NodeResources(1/3., 10/3., 100/3., 0.3).toString());
        assertEquals("[vcpu: 0.7, memory: 9.0 Gb, disk 66.7 Gb, bandwidth: 0.7 Gbps]",
                new NodeResources(2/3., 8.97, 200/3., 0.67).toString());
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
    @Test
    public void benchmark() {
        NodeResources [] resouces = new NodeResources[100];
        for (int i = 0; i < resouces.length; i++) {
            resouces[i] = new NodeResources(1/3., 10/3., 100/3., 0.3);
        }
        int NUM_ITER = 100; // Use at least 100000 for proper benchmarking
        long warmup = runTest(resouces, NUM_ITER);
        long start = System.nanoTime();
        long benchmark = runTest(resouces, NUM_ITER);
        long duration = System.nanoTime() - start;
        System.out.println("NodeResources.toString() took " + duration/1000000 + " ms");
        assertEquals(warmup, benchmark);
    }

}

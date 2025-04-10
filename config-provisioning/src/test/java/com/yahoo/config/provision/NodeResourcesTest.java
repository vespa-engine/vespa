// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.config.provision.NodeResources.Architecture;
import com.yahoo.config.provision.NodeResources.DiskSpeed;
import com.yahoo.config.provision.NodeResources.StorageType;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
class NodeResourcesTest {

    @Test
    void testCost() {
        assertEquals(5.408, new NodeResources(32, 128, 1200, 1).cost(), 0.0001);
    }

    @Test
    void testToString() {
        assertEquals("[vcpu: 1.0, memory: 10.0 Gb, disk: 100.0 Gb, architecture: any]",
                               new NodeResources(1., 10., 100., 0).toString());
        assertEquals("[vcpu: 0.3, memory: 3.3 Gb, disk: 33.3 Gb, bandwidth: 0.3 Gbps, architecture: any]",
                               new NodeResources(1 / 3., 10 / 3., 100 / 3., 0.3).toString());
        assertEquals("[vcpu: 0.7, memory: 9.0 Gb, disk: 66.7 Gb, bandwidth: 0.7 Gbps, architecture: any]",
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
    void testSatisfies() {
        var hostResources = new NodeResources(1, 2, 3, 1);
        assertTrue(hostResources.satisfies(new NodeResources(1, 2, 3, 1)));
        assertTrue(hostResources.satisfies(new NodeResources(1, 1, 1, 1)));
        assertFalse(hostResources.satisfies(new NodeResources(2, 2, 3, 1)));
        assertFalse(hostResources.satisfies(new NodeResources(1, 3, 3, 1)));
        assertFalse(hostResources.satisfies(new NodeResources(1, 2, 4, 1)));

        var gpuHostResources = new NodeResources(1, 2, 3, 1,
                                                 NodeResources.DiskSpeed.fast,
                                                 NodeResources.StorageType.local,
                                                 NodeResources.Architecture.x86_64,
                                                 new NodeResources.GpuResources(NodeResources.GpuType.T4, 1, 16));
        assertTrue(gpuHostResources.satisfies(new NodeResources(1, 2, 3, 1,
                                                                NodeResources.DiskSpeed.fast,
                                                                NodeResources.StorageType.local,
                                                                NodeResources.Architecture.x86_64,
                                                                new NodeResources.GpuResources(NodeResources.GpuType.T4, 1, 16))));
        assertFalse(gpuHostResources.satisfies(new NodeResources(1, 2, 3, 1,
                                                                 NodeResources.DiskSpeed.fast,
                                                                 NodeResources.StorageType.local,
                                                                 NodeResources.Architecture.x86_64,
                                                                 new NodeResources.GpuResources(NodeResources.GpuType.T4, 1, 32))));
        assertFalse(hostResources.satisfies(gpuHostResources));
        assertFalse(gpuHostResources.satisfies(hostResources));
        var newerGpuResources = new NodeResources(1, 2, 3, 1,
                                                  NodeResources.DiskSpeed.fast,
                                                  NodeResources.StorageType.local,
                                                  NodeResources.Architecture.x86_64,
                                                  new NodeResources.GpuResources(NodeResources.GpuType.L40S, 1, 48));
        assertFalse(newerGpuResources.satisfies(gpuHostResources));
        assertFalse(gpuHostResources.satisfies(newerGpuResources));
        assertTrue(newerGpuResources.satisfies(new NodeResources(1, 2, 3, 1,
                                                                 NodeResources.DiskSpeed.fast,
                                                                 NodeResources.StorageType.local,
                                                                 NodeResources.Architecture.any,
                                                                 new NodeResources.GpuResources(NodeResources.GpuType.L40S, 1, 48))));
    }

    @Test
    void benchmark() {
        NodeResources [] resources = new NodeResources[100];
        for (int i = 0; i < resources.length; i++) {
            resources[i] = new NodeResources(1 / 3., 10 / 3., 100 / 3., 0.3);
        }
        int NUM_ITER = 100; // Use at least 100000 for proper benchmarking
        long warmup = runTest(resources, NUM_ITER);
        long start = System.nanoTime();
        long benchmark = runTest(resources, NUM_ITER);
        long duration = System.nanoTime() - start;
        System.out.println("NodeResources.toString() took " + duration / 1000000 + " ms");
        assertEquals(warmup, benchmark);
    }

    @Test
    void testSpecifyFully() {
        NodeResources empty = new NodeResources(0, 0, 0, 0).with(DiskSpeed.any);

        assertEquals(0, empty.withUnspecifiedFieldsFrom(empty).vcpu());
        assertEquals(3, empty.withUnspecifiedFieldsFrom(empty.withVcpu(3)).vcpu());
        assertEquals(2, empty.withVcpu(2).withUnspecifiedFieldsFrom(empty.withVcpu(3)).vcpu());

        assertEquals(0, empty.withUnspecifiedFieldsFrom(empty).memoryGiB());
        assertEquals(3, empty.withUnspecifiedFieldsFrom(empty.withMemoryGiB(3)).memoryGiB());
        assertEquals(2, empty.withMemoryGiB(2).withUnspecifiedFieldsFrom(empty.withMemoryGiB(3)).memoryGiB());

        assertEquals(0, empty.withUnspecifiedFieldsFrom(empty).diskGb());
        assertEquals(3, empty.withUnspecifiedFieldsFrom(empty.withDiskGb(3)).diskGb());
        assertEquals(2, empty.withDiskGb(2).withUnspecifiedFieldsFrom(empty.withDiskGb(3)).diskGb());

        assertEquals(0, empty.withUnspecifiedFieldsFrom(empty).bandwidthGbps());
        assertEquals(3, empty.withUnspecifiedFieldsFrom(empty.withBandwidthGbps(3)).bandwidthGbps());
        assertEquals(2, empty.withBandwidthGbps(2).withUnspecifiedFieldsFrom(empty.withBandwidthGbps(3)).bandwidthGbps());

        assertEquals(Architecture.any, empty.withUnspecifiedFieldsFrom(empty).architecture());
        assertEquals(Architecture.arm64, empty.withUnspecifiedFieldsFrom(empty.with(Architecture.arm64)).architecture());
        assertEquals(Architecture.x86_64, empty.with(Architecture.x86_64).withUnspecifiedFieldsFrom(empty.with(Architecture.arm64)).architecture());

        assertEquals(DiskSpeed.any, empty.withUnspecifiedFieldsFrom(empty).diskSpeed());
        assertEquals(DiskSpeed.fast, empty.withUnspecifiedFieldsFrom(empty.with(DiskSpeed.fast)).diskSpeed());
        assertEquals(DiskSpeed.slow, empty.with(DiskSpeed.slow).withUnspecifiedFieldsFrom(empty.with(DiskSpeed.fast)).diskSpeed());

        assertEquals(StorageType.any, empty.withUnspecifiedFieldsFrom(empty).storageType());
        assertEquals(StorageType.local, empty.withUnspecifiedFieldsFrom(empty.with(StorageType.local)).storageType());
        assertEquals(StorageType.remote, empty.with(StorageType.remote).withUnspecifiedFieldsFrom(empty.with(StorageType.local)).storageType());
    }

    @Test
    void testJoiningResources() {
        var resources = new NodeResources(1, 2, 3, 1,
                NodeResources.DiskSpeed.fast,
                NodeResources.StorageType.local,
                NodeResources.Architecture.x86_64,
                new NodeResources.GpuResources(NodeResources.GpuType.T4, 4, 16));

        assertNotInterchangeable(resources, resources.with(NodeResources.DiskSpeed.slow));
        assertNotInterchangeable(resources, resources.with(NodeResources.StorageType.remote));
        assertNotInterchangeable(resources, resources.with(NodeResources.Architecture.arm64));

        var other = resources.with(new NodeResources.GpuResources(NodeResources.GpuType.T4, 4, 32));
        var expected = resources.withVcpu(2)
                .withMemoryGiB(4)
                .withDiskGb(6)
                .withBandwidthGbps(2)
                .with(new NodeResources.GpuResources(NodeResources.GpuType.T4, 1, 192));
        var actual = resources.add(other);
        assertEquals(expected, actual);

        // Subtracted back to original resources - but GPU is flattened to count=1
        expected = resources.with(new NodeResources.GpuResources(NodeResources.GpuType.T4, 1, 64));
        actual = actual.subtract(other);
        assertEquals(expected, actual);
    }

    private static NodeResources.GpuResources makeGpus(String t, int cnt, double mem) {
        return new NodeResources.GpuResources(t, cnt, mem);
    }

    @Test
    void testGpuArithmetic() {
        var zero = NodeResources.GpuResources.zero();
        var one = makeGpus("T4", 1, 16);
        var big = makeGpus("T4", 1, 32);
        var four = makeGpus("T4", 4, 16);
        var ampere = makeGpus("A100", 1, 40);
        var lovelace = makeGpus("L40S", 1, 48);
        assertEquals(zero, zero.plus(zero));
        assertEquals(one, zero.plus(one));
        assertEquals(four, zero.plus(four));
        assertEquals(ampere, zero.plus(ampere));
        assertEquals(one, one.plus(zero));
        assertEquals(one, one.minus(zero));
        assertEquals(big, one.multipliedBy(2));

        assertEquals(makeGpus("T4", 1, 80), one.plus(four));
        assertEquals(makeGpus("T4", 1, 80), four.plus(one));
        assertEquals(makeGpus("T4", 1, 96), big.plus(four));
        assertEquals(makeGpus("T4", 1, 96), four.plus(big));

        assertEquals(makeGpus("T4", 1, 48), four.minus(one));
        assertEquals(makeGpus("T4", 1, -16), zero.minus(one));
        assertEquals(makeGpus("T4", 1, -16), one.minus(big));
        assertEquals(makeGpus("T4", 1, -32), big.minus(four));

        // TODO - addition not commutative:
        assertEquals(makeGpus("T4", 1, 56), one.plus(ampere));
        assertEquals(makeGpus("T4", 1, 64), one.plus(lovelace));
        assertEquals(makeGpus("A100", 1, 56), ampere.plus(one));
        assertEquals(makeGpus("L40S", 1, 64), lovelace.plus(one));
    }


    // Comparing AWS (not Vespa) approx pricing for different disk options
    // @Test
    void ebs_vs_local_disk_pricing() {
        // https://aws.amazon.com/ebs/pricing/

        double pricePerNode = new NodeResources(32.0, 256, 7500, 1.0).cost() / 3;
        System.out.println("Price for 7500Gb with local disk:            " + pricePerNode + " $/hr");

        double totalPriceWithComparableIo2 = io2Price(1_000_000); // 1 disk does maybe 500k, each disk has size=3750Gb, so there are 2
        System.out.println("Price for 7500Gb io2 with comparable perf.: " + totalPriceWithComparableIo2 + " $/hr");
        double totalPriceWith16kIo2 = io2Price(16_000);
        System.out.println("Price for 7500Gb io2 16k iops:               " + totalPriceWith16kIo2 + " $/hr");
        double totalPriceWith3kIo2 = io2Price(3000); // What we get with gp3 (by default, and not flag overridden for anyone)
        System.out.println("Price for 7500Gb io2  3k iops:               " + totalPriceWith3kIo2 + " $/hr");

        double totalPriceWith16kGp3 = gp3Price(16_000);
        System.out.println("Price for 7500Gb gp3 16k iops:               " + totalPriceWith16kGp3 + " $/hr");
        double totalPriceWith3kGp3 = gp3Price(3000);
        System.out.println("Price for 7500Gb gp3  3k iops:               " + totalPriceWith3kGp3 + " $/hr");
    }

    private double io2Price(double iops) {
        double pricePerNodeNoDisk = new NodeResources(32.0, 256, 0, 1.0).cost() / 3;
        double priceForIo21Gbhr = 0.125/(30*24);
        double priceForComparableIops = 0.046/(30*24) * iops; // approx: mid range price
        return pricePerNodeNoDisk + 7500 * priceForIo21Gbhr + priceForComparableIops;
    }

    private double gp3Price(double iops) {
        double pricePerNodeNoDisk = new NodeResources(32.0, 256, 0, 1.0).cost() / 3;
        double priceForIo21Gbhr = 0.08 / (30 * 24);
        double priceForComparableIops = 0.005 / (30 * 24) * (iops - 3000);
        return pricePerNodeNoDisk + 7500 * priceForIo21Gbhr + priceForComparableIops;
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

    private void assertNotInterchangeable(NodeResources a, NodeResources b) {
        try {
            a.add(b);
            fail();
        }
        catch (IllegalArgumentException e) {
            assertEquals(a + " and " + b + " are not interchangeable", e.getMessage());
        }
    }

}

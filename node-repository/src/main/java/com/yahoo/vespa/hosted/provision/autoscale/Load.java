// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.NodeResources;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;

/**
 * The load of a node or system, measured as fractions of max (1.0) in three dimensions.
 *
 * @author bratseth
 */
public record Load(double cpu, double memory, double disk, double gpu, double gpuMemory) {

    public enum Dimension { cpu, memory, disk, gpu, gpuMemory }

    public Load(double cpu, double memory, double disk, double gpu, double gpuMemory) {
        this.cpu = requireNormalized(cpu, "cpu");
        this.memory = requireNormalized(memory, "memory");
        this.disk = requireNormalized(disk, "disk");
        this.gpu = requireNormalized(gpu, "gpu");
        this.gpuMemory = requireNormalized(gpuMemory, "gpuMemory");
    }

    public double cpu() { return cpu; }
    public double memory() { return memory; }
    public double disk() { return disk; }
    public double gpu() { return gpu; }
    public double gpuMemory() { return gpuMemory; }

    public Load withCpu(double cpu) { return new Load(cpu, memory, disk, gpu, gpuMemory); }
    public Load withMemory(double memory) { return new Load(cpu, memory, disk, gpu, gpuMemory); }
    public Load withDisk(double disk) { return new Load(cpu, memory, disk, gpu, gpuMemory); }
    public Load withGpu(double gpu) { return new Load(cpu, memory, disk, gpu, gpuMemory); }
    public Load withGpuMemory(double gpuMemory) { return new Load(cpu, memory, disk, gpu, gpuMemory); }

    public Load add(Load other) {
        return join(other, (a, b) -> a + b);
    }

    public Load multiply(NodeResources resources) {
        return new Load(cpu * resources.vcpu(), memory * resources.memoryGb(), disk * resources.diskGb(), gpu * resources.gpuResources().count(), gpu * resources.gpuResources().memoryGb());
    }
    public Load multiply(double factor) {
        return map(v -> v * factor);
    }
    public Load multiply(Load other) {
        return join(other, (a, b) -> a * b);
    }

    public Load divide(Load divisor) {
        return join(divisor, (a, b) -> divide(a, b));
    }
    public Load divide(double divisor) {
        return map(v -> divide(v, divisor));
    }
    public Load divide(NodeResources resources) {
        return new Load(divide(cpu, resources.vcpu()), divide(memory, resources.memoryGb()), divide(disk, resources.diskGb()), divide(gpu, resources.gpuResources().count()), divide(gpuMemory, resources.gpuResources().memoryGb()));
    }

    /** Returns the load where the given function is applied to each dimension of this. */
    public Load map(DoubleUnaryOperator f) {
        return new Load(f.applyAsDouble(cpu),
                        f.applyAsDouble(memory),
                        f.applyAsDouble(disk),
                        f.applyAsDouble(gpu),
                        f.applyAsDouble(gpuMemory));
    }

    /** Returns the load where the given function is applied to each dimension of this and the given load. */
    public Load join(Load other, DoubleBinaryOperator f) {
        return new Load(f.applyAsDouble(this.cpu(), other.cpu()),
                        f.applyAsDouble(this.memory(), other.memory()),
                        f.applyAsDouble(this.disk(), other.disk()),
                        f.applyAsDouble(this.gpu(), other.gpu()),
                        f.applyAsDouble(this.gpuMemory(), other.gpuMemory()));
    }

    /** Returns true if any dimension matches the predicate. */
    public boolean any(Predicate<Double> test) {
        return test.test(cpu) || test.test(memory) || test.test(disk);
    }

    public NodeResources scaled(NodeResources resources) {
        return resources.withVcpu(cpu * resources.vcpu())
                        .withMemoryGb(memory * resources.memoryGb())
                        .withDiskGb(disk * resources.diskGb());
    }

    public double get(Dimension dimension) {
        return switch (dimension) {
            case cpu -> cpu();
            case memory -> memory();
            case disk -> disk();
            case gpu -> gpu();
            case gpuMemory -> gpuMemory();
        };
    }

    private double requireNormalized(double value, String name) {
        if (Double.isNaN(value))
            throw new IllegalArgumentException(name + " must be a number but is NaN");
        if (value < 0)
            throw new IllegalArgumentException(name + " must be zero or larger, but is " + value);
        return value;
    }

    private static double divide(double a, double b) {
        if (a == 0 && b == 0) return 0;
        return a / b;
    }

    @Override
    public String toString() {
        return "load: " + cpu + " cpu, " + memory + " memory, " + disk + " disk, " + gpu + " gpu, " + gpuMemory + " gpuMemory";
    }

    public static Load zero() { return new Load(0, 0, 0, 0, 0); }
    public static Load one() { return new Load(1, 1, 1, 1, 1); }

    public static Load byDividing(NodeResources a, NodeResources b) {
        return new Load(divide(a.vcpu(), b.vcpu()),
                        divide(a.memoryGb(), b.memoryGb()),
                        divide(a.diskGb(), b.diskGb()),
                        divide(a.gpuResources().count(), b.gpuResources().count()),
                        divide(a.gpuResources().memoryGb(), b.gpuResources().memoryGb()));
    }

}

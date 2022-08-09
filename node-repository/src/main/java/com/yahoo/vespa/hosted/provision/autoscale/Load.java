// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.NodeResources;

import java.util.Objects;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;

/**
 * The load of a node or system, measured as fractions of max (1.0) in three dimensions.
 *
 * @author bratseth
 */
public class Load {

    public enum Dimension { cpu, memory, disk }

    private final double cpu, memory, disk;

    public Load(double cpu, double memory, double disk) {
        this.cpu = requireNormalized(cpu, "cpu");
        this.memory = requireNormalized(memory, "memory");
        this.disk = requireNormalized(disk, "disk");
    }

    public double cpu() { return cpu; }
    public double memory() { return memory; }
    public double disk() { return disk; }

    public Load add(Load other) {
        return join(other, (a, b) -> a + b);
    }

    public Load multiply(NodeResources resources) {
        return new Load(cpu * resources.vcpu(), memory * resources.memoryGb(), disk * resources.diskGb());
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
        return new Load(divide(cpu, resources.vcpu()), divide(memory, resources.memoryGb()), divide(disk, resources.diskGb()));
    }

    /** Returns the load where the given function is applied to each dimension of this. */
    public Load map(DoubleUnaryOperator f) {
        return new Load(f.applyAsDouble(cpu),
                        f.applyAsDouble(memory),
                        f.applyAsDouble(disk));
    }

    /** Returns the load where the given function is applied to each dimension of this and the given load. */
    public Load join(Load other, DoubleBinaryOperator f) {
        return new Load(f.applyAsDouble(this.cpu(), other.cpu()),
                        f.applyAsDouble(this.memory(), other.memory()),
                        f.applyAsDouble(this.disk(), other.disk()));
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
        };
    }

    private double requireNormalized(double value, String name) {
        if (Double.isNaN(value))
            throw new IllegalArgumentException(name + " must be a number but is NaN");
        if (value < 0)
            throw new IllegalArgumentException(name + " must be zero or lager, but is " + value);
        return value;
    }

    private static double divide(double a, double b) {
        if (a == 0 && b == 0) return 0;
        return a / b;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Load other)) return false;
        if (other.cpu() != this.cpu()) return false;
        if (other.memory() != this.memory()) return false;
        if (other.disk() != this.disk()) return false;
        return true;
    }

    @Override
    public int hashCode() { return Objects.hash(cpu, memory, disk); }

    @Override
    public String toString() {
        return "load: " + cpu + " cpu, " + memory + " memory, " + disk + " disk";
    }

    public static Load zero() { return new Load(0, 0, 0); }
    public static Load one() { return new Load(1, 1, 1); }

    public static Load byDividing(NodeResources a, NodeResources b) {
        return new Load(divide(a.vcpu(), b.vcpu()), divide(a.memoryGb(), b.memoryGb()), divide(a.diskGb(), b.diskGb()));
    }

}

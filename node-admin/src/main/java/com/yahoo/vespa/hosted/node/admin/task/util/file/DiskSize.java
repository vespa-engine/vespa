// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.util.Locale;
import java.util.Objects;

/**
 * @author freva
 */
public class DiskSize implements Comparable<DiskSize> {

    public static final DiskSize ZERO = DiskSize.of(0);
    private static final char[] UNITS = "kMGTPE".toCharArray();

    public enum Unit {
        kB(1_000), kiB(1 << 10),
        MB(1_000_000), MiB(1 << 20),
        GB(1_000_000_000), GiB(1 << 30),
        TB(1_000_000_000_000L), TiB(1L << 40),
        PB(1_000_000_000_000_000L), PiB(1L << 50);

        private final long size;
        Unit(long size) { this.size = size; }
    }

    private final long bytes;
    private DiskSize(long bytes) { this.bytes = bytes; }

    public long bytes() { return bytes; }
    public long as(Unit unit) { return bytes / unit.size; }
    public double asDouble(Unit unit) { return (double) bytes / unit.size; }

    public DiskSize add(DiskSize other) { return new DiskSize(bytes + other.bytes); }

    public static DiskSize of(long bytes) { return new DiskSize(bytes); }
    public static DiskSize of(double bytes, Unit unit) { return new DiskSize((long) (bytes * unit.size)); }
    public static DiskSize of(long bytes, Unit unit) { return new DiskSize(bytes * unit.size); }

    public String asString() { return asString(0); }
    public String asString(int decimals) {
        if (bytes < 1000) return bytes + " bytes";

        int unit = -1;
        double remaining = bytes;
        for (; remaining >= 1000; unit++) remaining /= 1000;
        return String.format(Locale.ENGLISH, "%." + decimals + "f %sB", remaining, UNITS[unit]);
    }

    @Override
    public int compareTo(DiskSize rhs) {
        return Long.compare(this.bytes, rhs.bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiskSize size = (DiskSize) o;
        return bytes == size.bytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes);
    }

    @Override
    public String toString() {
        return asString();
    }
}

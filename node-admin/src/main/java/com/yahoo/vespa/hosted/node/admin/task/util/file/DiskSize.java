package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;
import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * @author freva
 */
public class DiskSize {

    public static final DiskSize ZERO = DiskSize.of(0);
    private static final char[] UNITS = "kMGTPE".toCharArray();

    public enum Unit {
        kB(1000), kiB(1 << 10),
        MB(1_000_000), MiB(1 << 20),
        GB(1_000_000_000), GiB(1 << 30),
        PB(1_000_000_000_000L), PiB(1L << 40);

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
        return String.format("%." + decimals + "f %sB", remaining, UNITS[unit]);
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

    /** Measure the total size of given path */
    public static DiskSize measure(Path path, CommandLine commandLine) {
        if (!Files.exists(path)) return DiskSize.ZERO;
        String output = commandLine.add("du", "-xsk", path.toString())
                                   .setTimeout(Duration.ofSeconds(60))
                                   .executeSilently()
                                   .getOutput();
        String[] parts = output.split("\t");
        if (parts.length != 2) {
            throw new ConvergenceException("Result from disk usage command not as expected: '" + output + "'");
        }
        return DiskSize.of(Long.parseLong(parts[0]), DiskSize.Unit.kiB);
    }

}

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.task.util.process.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
        if (!Files.exists(path)) return ZERO;
        String output = commandLine.add("du", "-xsk", path.toString())
                                   .setTimeout(Duration.ofSeconds(60))
                                   .executeSilently()
                                   .getOutput();
        String[] parts = output.split("\t");
        if (parts.length != 2) unexpectedOutput("du", output);
        return DiskSize.of(Long.parseLong(parts[0]), Unit.kiB);
    }


    /** Return the size of the local partition where path is located */
    public static PartitionSize partition(Path path, CommandLine commandLine) {
        if (!Files.exists(path)) return PartitionSize.ZERO;
        List<String> lines = commandLine.add("df", "--portability", "--local", "--block-size", "1K", path.toString())
                                        .setTimeout(Duration.ofSeconds(60))
                                        .executeSilently()
                                        .getOutputLines();
        if (lines.size() != 2) unexpectedOutput("df", lines.toString());
        String[] parts = lines.get(1).split("\\s+");
        if (parts.length != 6) unexpectedOutput("df", lines.toString());
        DiskSize total = DiskSize.of(Long.parseLong(parts[1]), Unit.kiB);
        DiskSize used = DiskSize.of(Long.parseLong(parts[2]), Unit.kiB);
        DiskSize available = DiskSize.of(Long.parseLong(parts[3]), Unit.kiB);
        return new PartitionSize(total, used, available);
    }

    private static void unexpectedOutput(String command, String output) {
        throw new IllegalArgumentException("Unexpected output from " + command + ": '" + output + "'");
    }

    /** Represents the size of a disk partition */
    public static class PartitionSize {

        public static final PartitionSize ZERO = new PartitionSize(DiskSize.ZERO, DiskSize.ZERO, DiskSize.ZERO);

        private final DiskSize total;
        private final DiskSize used;
        private final DiskSize available;

        public PartitionSize(DiskSize total, DiskSize used, DiskSize available) {
            this.total = total;
            this.used = used;
            this.available = available;
        }

        public DiskSize total() { return total; }

        public DiskSize used() {
            return used;
        }

        public DiskSize available() {
            return available;
        }

    }

}

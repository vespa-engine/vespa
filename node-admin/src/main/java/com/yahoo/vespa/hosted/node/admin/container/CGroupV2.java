// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Read and write interface to the cgroup v2 of a Podman container.
 *
 * @see <a href="https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html">CGroups V2</a>
 * @author freva
 */
public class CGroupV2 {

    private static final Logger logger = Logger.getLogger(CGroupV2.class.getName());
    private static final String MAX = "max";

    private final Path rootCgroupPath;

    public CGroupV2(FileSystem fileSystem) {
        this.rootCgroupPath = fileSystem.getPath("/sys/fs/cgroup");
    }

    /**
     * Wraps {@code command} to ensure it is executed in the given cgroup.
     *
     * <p>WARNING: This method must be called only after vespa-cgexec has been installed.</p>
     *
     * @param cgroup  The cgroup to execute the command in, e.g. /sys/fs/cgroup/system.slice/wireguard.scope.
     * @param command The command to execute in the cgroup.
     * @see #cgroupRootPath()
     * @see #cgroupPath(ContainerId)
     */
    public String[] wrapForExecutionIn(Path cgroup, String... command) {
        String[] fullCommand = new String[3 + command.length];
        fullCommand[0] = Defaults.getDefaults().vespaHome() + "/bin/vespa-cgexec";
        fullCommand[1] = "-g";
        fullCommand[2] = cgroup.toString();
        System.arraycopy(command, 0, fullCommand, 3, command.length);
        return fullCommand;
    }

    /**
     * Returns quota and period values used for CPU scheduling. This serves as hard cap on CPU usage by allowing
     * the CGroupV2 to use up to {@code quota} each {@code period}. If uncapped, quota will be negative.
     *
     * @param containerId full container ID.
     * @return CPU quota and period for the given container. Empty if CGroupV2 for this container is not found.
     */
    public Optional<Pair<Integer, Integer>> cpuQuotaPeriod(ContainerId containerId) {
        return cpuMaxPath(containerId).readUtf8FileIfExists()
                .map(s -> {
                    String[] parts = s.strip().split(" ");
                    return new Pair<>(MAX.equals(parts[0]) ? -1 : Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                });
    }

    /** @return number of shares allocated to this CGroupV2 for purposes of CPU time scheduling, empty if CGroupV2 not found */
    public OptionalInt cpuShares(ContainerId containerId) {
        return cpuWeightPath(containerId).readUtf8FileIfExists()
                .map(s -> OptionalInt.of(weightToShares(Integer.parseInt(s.strip()))))
                .orElseGet(OptionalInt::empty);
    }

    /** Update CPU quota and period for the given container ID, set quota to -1 value for unlimited */
    public boolean updateCpuQuotaPeriod(NodeAgentContext context, ContainerId containerId, int cpuQuotaUs, int periodUs) {
        String wanted = String.format("%s %d", cpuQuotaUs < 0 ? MAX : cpuQuotaUs, periodUs);
        return writeCGroupsValue(context, cpuMaxPath(containerId), wanted);
    }

    public boolean updateCpuShares(NodeAgentContext context, ContainerId containerId, int shares) {
        return writeCGroupsValue(context, cpuWeightPath(containerId), Integer.toString(sharesToWeight(shares)));
    }

    enum CpuStatField {
        TOTAL_USAGE_USEC("usage_usec"),
        USER_USAGE_USEC("user_usec"),
        SYSTEM_USAGE_USEC("system_usec"),
        TOTAL_PERIODS("nr_periods"),
        THROTTLED_PERIODS("nr_throttled"),
        THROTTLED_TIME_USEC("throttled_usec");

        private final String name;

        CpuStatField(String name) {
            this.name = name;
        }

        long parseValue(String value) {
            return Long.parseLong(value);
        }

        static Optional<CpuStatField> fromField(String fieldName) {
            return Arrays.stream(values())
                         .filter(field -> fieldName.equals(field.name))
                         .findFirst();
        }
    }

    public Map<CpuStatField, Long> cpuStats(ContainerId containerId) throws IOException {
        return Files.readAllLines(cgroupPath(containerId).resolve("cpu.stat")).stream()
                    .map(line -> line.split("\\s+"))
                    .filter(parts -> parts.length == 2)
                    .flatMap(parts -> CpuStatField.fromField(parts[0]).stream().map(field -> new Pair<>(field, field.parseValue(parts[1]))))
                    .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    /** @return Maximum amount of memory that can be used by the cgroup and its descendants. */
    public long memoryLimitInBytes(ContainerId containerId) throws IOException {
        String limit = Files.readString(cgroupPath(containerId).resolve("memory.max")).strip();
        return MAX.equals(limit) ? -1L : Long.parseLong(limit);
    }

    /** @return The total amount of memory currently being used by the cgroup and its descendants. */
    public long memoryUsageInBytes(ContainerId containerId) throws IOException {
        return parseLong(cgroupPath(containerId).resolve("memory.current"));
    }

    /** @return Number of bytes used to cache filesystem data, including tmpfs and shared memory. */
    public long memoryCacheInBytes(ContainerId containerId) throws IOException {
        return parseLong(cgroupPath(containerId).resolve("memory.stat"), "file");
    }

    /** Returns the cgroup v2 mount point path (/sys/fs/cgroup). */
    public Path cgroupRootPath() {
        return rootCgroupPath;
    }

    /** Returns the cgroup directory of the Podman container, and which appears as the root cgroup within the container. */
    public Path cgroupPath(ContainerId containerId) {
        // crun path, runc path is without the 'container' directory
        return rootCgroupPath.resolve("machine.slice/libpod-" + containerId + ".scope/container");
    }

    private UnixPath cpuMaxPath(ContainerId containerId) {
        return new UnixPath(cgroupPath(containerId).resolve("cpu.max"));
    }

    private UnixPath cpuWeightPath(ContainerId containerId) {
        return new UnixPath(cgroupPath(containerId).resolve("cpu.weight"));
    }

    private static boolean writeCGroupsValue(NodeAgentContext context, UnixPath unixPath, String value) {
        String currentValue = unixPath.readUtf8File().strip();
        if (currentValue.equals(value)) return false;

        context.recordSystemModification(logger, "Updating " + unixPath + " from " + currentValue + " to " + value);
        unixPath.writeUtf8File(value);
        return true;
    }

    // Must be same as in crun: https://github.com/containers/crun/blob/72c6e60ade0e4716fe2d8353f0d97d72cc8d1510/src/libcrun/cgroup.c#L3061
    static int sharesToWeight(int shares) { return (int) (1 + ((shares - 2L) * 9999) / 262142); }
    static int weightToShares(int weight) { return (int) (2 + ((weight - 1L) * 262142) / 9999); }

    static long parseLong(Path path) throws IOException {
        return Long.parseLong(Files.readString(path).trim());
    }

    static long parseLong(Path path, String fieldName) throws IOException {
        return parseLong(Files.readAllLines(path), fieldName);
    }

    static long parseLong(List<String> lines, String fieldName) {
        for (String line : lines) {
            String[] fields = line.split("\\s+");
            if (fields.length != 2)
                throw new IllegalArgumentException("Expected line on the format 'key value', got: '" + line + "'");

            if (fieldName.equals(fields[0])) return Long.parseLong(fields[1]);
        }
        throw new IllegalArgumentException("No such field: " + fieldName);
    }
}

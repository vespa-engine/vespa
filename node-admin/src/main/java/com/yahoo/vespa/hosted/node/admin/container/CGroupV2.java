// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Read and write interface to the CGroup V2 of a Podman container.
 *
 * @see <a href="https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html">CGroups V2</a>
 * @author freva
 */
public class CGroupV2 implements CGroup {

    private static final Logger logger = Logger.getLogger(CGroupV2.class.getName());
    private static final String MAX = "max";

    private final FileSystem fileSystem;

    public CGroupV2(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public Optional<Pair<Integer, Integer>> cpuQuotaPeriod(ContainerId containerId) {
        return cpuMaxPath(containerId).readUtf8FileIfExists()
                .map(s -> {
                    String[] parts = s.strip().split(" ");
                    return new Pair<>(MAX.equals(parts[0]) ? -1 : Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                });
    }

    @Override
    public OptionalInt cpuShares(ContainerId containerId) {
        return cpuWeightPath(containerId).readUtf8FileIfExists()
                .map(s -> OptionalInt.of(weightToShares(Integer.parseInt(s.strip()))))
                .orElseGet(OptionalInt::empty);
    }

    @Override
    public boolean updateCpuQuotaPeriod(NodeAgentContext context, ContainerId containerId, int cpuQuotaUs, int periodUs) {
        String wanted = String.format("%s %d", cpuQuotaUs < 0 ? MAX : cpuQuotaUs, periodUs);
        return writeCGroupsValue(context, cpuMaxPath(containerId), wanted);
    }

    @Override
    public boolean updateCpuShares(NodeAgentContext context, ContainerId containerId, int shares) {
        return writeCGroupsValue(context, cpuWeightPath(containerId), Integer.toString(sharesToWeight(shares)));
    }

    @Override
    public Map<CpuStatField, Long> cpuStats(ContainerId containerId) throws IOException {
        return Files.readAllLines(cgroupRoot(containerId).resolve("cpu.stat")).stream()
                .map(line -> line.split("\\s+"))
                .filter(parts -> parts.length == 2)
                .flatMap(parts -> CpuStatField.fromV2Field(parts[0]).stream().map(field -> new Pair<>(field, field.parseValueV2(parts[1]))))
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    @Override
    public long memoryLimitInBytes(ContainerId containerId) throws IOException {
        String limit = Files.readString(cgroupRoot(containerId).resolve("memory.max")).strip();
        return MAX.equals(limit) ? -1L : Long.parseLong(limit);
    }

    @Override
    public long memoryUsageInBytes(ContainerId containerId) throws IOException {
        return parseLong(cgroupRoot(containerId).resolve("memory.current"));
    }

    @Override
    public long memoryCacheInBytes(ContainerId containerId) throws IOException {
        return parseLong(cgroupRoot(containerId).resolve("memory.stat"), "file");
    }

    private Path cgroupRoot(ContainerId containerId) {
        // crun path, runc path is without the 'container' directory
        return fileSystem.getPath("/sys/fs/cgroup/machine.slice/libpod-" + containerId + ".scope/container");
    }

    private UnixPath cpuMaxPath(ContainerId containerId) {
        return new UnixPath(cgroupRoot(containerId).resolve("cpu.max"));
    }

    private UnixPath cpuWeightPath(ContainerId containerId) {
        return new UnixPath(cgroupRoot(containerId).resolve("cpu.weight"));
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

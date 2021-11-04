// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.node.admin.container.CGroupV2.parseLong;

/**
 * Read and write interface to the CGroup V1 of a Podman container.
 *
 * @see <a href="https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v1/index.html">CGroups V1</a>
 * @author freva
 */
public class CGroupV1 implements CGroup {

    private static final Logger logger = Logger.getLogger(CGroupV1.class.getName());

    private final FileSystem fileSystem;

    public CGroupV1(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public Optional<Pair<Integer, Integer>> cpuQuotaPeriod(ContainerId containerId) {
        OptionalInt quota = readCgroupsCpuInt(cfsQuotaPath(containerId));
        if (quota.isEmpty()) return Optional.empty();
        OptionalInt period = readCgroupsCpuInt(cfsPeriodPath(containerId));
        if (period.isEmpty()) return Optional.empty();
        return Optional.of(new Pair<>(quota.getAsInt(), period.getAsInt()));
    }

    @Override
    public OptionalInt cpuShares(ContainerId containerId) {
        return readCgroupsCpuInt(sharesPath(containerId));
    }

    @Override
    public boolean updateCpuQuotaPeriod(NodeAgentContext context, ContainerId containerId, int cpuQuotaUs, int periodUs) {
        return writeCgroupsCpuInt(context, cfsQuotaPath(containerId), cpuQuotaUs) |
               writeCgroupsCpuInt(context, cfsPeriodPath(containerId), periodUs);
    }

    @Override
    public boolean updateCpuShares(NodeAgentContext context, ContainerId containerId, int shares) {
        return writeCgroupsCpuInt(context, sharesPath(containerId), shares);
    }

    @Override
    public Map<CpuStatField, Long> cpuStats(ContainerId containerId) throws IOException {
        Map<CpuStatField, Long> stats = new HashMap<>();
        stats.put(CpuStatField.TOTAL_USAGE_USEC, parseLong(cpuacctPath(containerId).resolve("cpuacct.usage")) / 1000);
        Stream.concat(Files.readAllLines(cpuacctPath(containerId).resolve("cpuacct.stat")).stream(),
                      Files.readAllLines(cpuacctPath(containerId).resolve("cpu.stat")).stream())
                .forEach(line -> {
                    String[] parts = line.split("\\s+");
                    if (parts.length != 2) return;
                    CpuStatField.fromV1Field(parts[0]).ifPresent(field -> stats.put(field, field.parseValueV1(parts[1])));
                });
        return stats;
    }

    @Override
    public long memoryLimitInBytes(ContainerId containerId) throws IOException {
        return parseLong(memoryPath(containerId).resolve("memory.limit_in_bytes"));
    }

    @Override
    public long memoryUsageInBytes(ContainerId containerId) throws IOException {
        return parseLong(memoryPath(containerId).resolve("memory.usage_in_bytes"));
    }

    @Override
    public long memoryCacheInBytes(ContainerId containerId) throws IOException {
        return parseLong(memoryPath(containerId).resolve("memory.stat"), "cache");
    }

    private Path cpuacctPath(ContainerId containerId) {
        return fileSystem.getPath("/sys/fs/cgroup/cpuacct/machine.slice/libpod-" + containerId + ".scope");
    }

    private Path cpuPath(ContainerId containerId) {
        return fileSystem.getPath("/sys/fs/cgroup/cpu/machine.slice/libpod-" + containerId + ".scope");
    }

    private Path memoryPath(ContainerId containerId) {
        return fileSystem.getPath("/sys/fs/cgroup/memory/machine.slice/libpod-" + containerId + ".scope");
    }

    private UnixPath cfsQuotaPath(ContainerId containerId) {
        return new UnixPath(cpuPath(containerId).resolve("cpu.cfs_quota_us"));
    }

    private UnixPath cfsPeriodPath(ContainerId containerId) {
        return new UnixPath(cpuPath(containerId).resolve("cpu.cfs_period_us"));
    }

    private UnixPath sharesPath(ContainerId containerId) {
        return new UnixPath(cpuPath(containerId).resolve("cpu.shares"));
    }

    private static OptionalInt readCgroupsCpuInt(UnixPath unixPath) {
        return unixPath.readUtf8FileIfExists()
                .map(s -> OptionalInt.of(Integer.parseInt(s.strip())))
                .orElseGet(OptionalInt::empty);
    }

    private static boolean writeCgroupsCpuInt(NodeAgentContext context, UnixPath unixPath, int value) {
        int currentValue = readCgroupsCpuInt(unixPath).orElseThrow();
        if (currentValue == value) return false;

        context.recordSystemModification(logger, "Updating " + unixPath + " from " + currentValue + " to " + value);
        unixPath.writeUtf8File(Integer.toString(value));
        return true;
    }
}

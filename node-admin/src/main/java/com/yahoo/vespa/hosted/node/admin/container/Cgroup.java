// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.logging.Logger;

/**
 * Read and write interface to the v1 cgroup of a podman container.
 * See <a href="https://man7.org/linux/man-pages/man7/cgroups.7.html">cgroups(7)</a> for background.
 *
 * @author hakon
 */
public class Cgroup {

    private static final Logger logger = Logger.getLogger(Cgroup.class.getName());

    private final FileSystem fileSystem;
    private final ContainerId containerId;

    public Cgroup(FileSystem fileSystem, ContainerId containerId) {
        this.fileSystem = fileSystem;
        this.containerId = containerId;
    }

    public OptionalInt readCpuQuota() {
        return readCgroupsCpuInt(cfsQuotaPath());
    }

    public OptionalInt readCpuPeriod() {
        return readCgroupsCpuInt(cfsPeriodPath());
    }

    public OptionalInt readCpuShares() {
        return readCgroupsCpuInt(sharesPath());
    }

    public boolean updateCpuQuota(NodeAgentContext context, int cpuQuotaUs) {
        return writeCgroupsCpuInt(context, cfsQuotaPath(), cpuQuotaUs);
    }

    public boolean updateCpuPeriod(NodeAgentContext context, int periodUs) {
        return writeCgroupsCpuInt(context, cfsPeriodPath(), periodUs);
    }

    public boolean updateCpuShares(NodeAgentContext context, int shares) {
        return writeCgroupsCpuInt(context, sharesPath(), shares);
    }

    /** Returns the path to the podman container's scope directory for the cpuacct controller. */
    public Path cpuacctPath() {
        return fileSystem.getPath("/sys/fs/cgroup/cpuacct/machine.slice/libpod-" + containerId + ".scope");
    }

    /** Returns the path to the podman container's scope directory for the cpu controller. */
    public Path cpuPath() {
        return fileSystem.getPath("/sys/fs/cgroup/cpu/machine.slice/libpod-" + containerId + ".scope");
    }

    /** Returns the path to the podman container's scope directory for the memory controller. */
    public Path memoryPath() {
        return fileSystem.getPath("/sys/fs/cgroup/memory/machine.slice/libpod-" + containerId + ".scope");
    }

    private UnixPath cfsQuotaPath() {
        return new UnixPath(cpuPath().resolve("cpu.cfs_quota_us"));
    }

    private UnixPath cfsPeriodPath() {
        return new UnixPath(cpuPath().resolve("cpu.cfs_period_us"));
    }

    private UnixPath sharesPath() {
        return new UnixPath(cpuPath().resolve("cpu.shares"));
    }

    private OptionalInt readCgroupsCpuInt(UnixPath unixPath) {
        final byte[] currentContentBytes;
        try {
            currentContentBytes = Files.readAllBytes(unixPath.toPath());
        } catch (NoSuchFileException e) {
            return OptionalInt.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String currentContent = new String(currentContentBytes, StandardCharsets.UTF_8).strip();
        return OptionalInt.of(Integer.parseInt(currentContent));
    }

    private boolean writeCgroupsCpuInt(NodeAgentContext context, UnixPath unixPath, int value) {
        int currentValue = readCgroupsCpuInt(unixPath).orElseThrow();
        if (currentValue == value) {
            return false;
        }

        context.recordSystemModification(logger, "Updating " + unixPath + " from " + currentValue + " to " + value);
        unixPath.writeUtf8File(Integer.toString(value));
        return true;
    }
}

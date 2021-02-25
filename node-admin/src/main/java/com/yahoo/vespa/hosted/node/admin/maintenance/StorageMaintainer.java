// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.maintenance.coredump.CoredumpHandler;
import com.yahoo.vespa.hosted.node.admin.maintenance.disk.CoredumpCleanupRule;
import com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanup;
import com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule;
import com.yahoo.vespa.hosted.node.admin.maintenance.disk.LinearCleanupRule;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncClient;
import com.yahoo.vespa.hosted.node.admin.maintenance.sync.SyncFileInfo;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentTask;
import com.yahoo.vespa.hosted.node.admin.task.util.file.DiskSize;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule.Priority;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author freva
 */
public class StorageMaintainer {
    private static final Logger logger = Logger.getLogger(StorageMaintainer.class.getName());
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final Terminal terminal;
    private final CoredumpHandler coredumpHandler;
    private final DiskCleanup diskCleanup;
    private final SyncClient syncClient;
    private final Clock clock;
    private final Path archiveContainerStoragePath;

    // We cache disk usage to avoid doing expensive disk operations so often
    private final Cache<ContainerName, DiskSize> diskUsage = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public StorageMaintainer(Terminal terminal, CoredumpHandler coredumpHandler, DiskCleanup diskCleanup,
                             SyncClient syncClient, Clock clock, Path archiveContainerStoragePath) {
        this.terminal = terminal;
        this.coredumpHandler = coredumpHandler;
        this.diskCleanup = diskCleanup;
        this.syncClient = syncClient;
        this.clock = clock;
        this.archiveContainerStoragePath = archiveContainerStoragePath;
    }

    public boolean syncLogs(NodeAgentContext context, boolean throttle) {
        Optional<URI> archiveUri = context.node().archiveUri();
        if (archiveUri.isEmpty()) return false;

        List<SyncFileInfo> syncFileInfos = FileFinder.files(pathOnHostUnderContainerVespaHome(context, "logs/vespa"))
                .maxDepth(2)
                .stream()
                .sorted(Comparator.comparing(FileFinder.FileAttributes::lastModifiedTime))
                .flatMap(fa -> SyncFileInfo.forLogFile(archiveUri.get(), fa.path(), throttle).stream())
                .collect(Collectors.toList());

        return syncClient.sync(context, syncFileInfos, throttle ? 1 : 100);
    }

    public Optional<DiskSize> diskUsageFor(NodeAgentContext context) {
        try {
            DiskSize cachedDiskUsage = diskUsage.getIfPresent(context.containerName());
            if (cachedDiskUsage != null) return Optional.of(cachedDiskUsage);

            DiskSize diskUsageBytes = getDiskUsed(context, context.pathOnHostFromPathInNode("/"));
            diskUsage.put(context.containerName(), diskUsageBytes);
            return Optional.of(diskUsageBytes);
        } catch (Exception e) {
            context.log(logger, Level.WARNING, "Failed to get disk usage", e);
            return Optional.empty();
        }
    }

    DiskSize getDiskUsed(TaskContext context, Path path) {
        if (!Files.exists(path)) return DiskSize.ZERO;

        String output = terminal.newCommandLine(context)
                .add("du", "-xsk", path.toString())
                .setTimeout(Duration.ofSeconds(60))
                .executeSilently()
                .getOutput();

        String[] results = output.split("\t");
        if (results.length != 2) {
            throw new ConvergenceException("Result from disk usage command not as expected: " + output);
        }

        return DiskSize.of(Long.parseLong(results[0]), DiskSize.Unit.kiB);
    }

    public boolean cleanDiskIfFull(NodeAgentContext context) {
        if (context.isDisabled(NodeAgentTask.DiskCleanup)) return false;

        double totalBytes = context.node().diskSize().bytes();
        // Delete enough bytes to get below 70% disk usage, but only if we are already using more than 80% disk
        long bytesToRemove = diskUsageFor(context)
                .map(diskUsage -> (long) (diskUsage.bytes() - 0.7 * totalBytes))
                .filter(bytes -> bytes > totalBytes * 0.1)
                .orElse(0L);

        if (bytesToRemove > 0 && diskCleanup.cleanup(context, createCleanupRules(context), bytesToRemove)) {
            diskUsage.invalidate(context.containerName());
            return true;
        }
        return false;
    }

    private List<DiskCleanupRule> createCleanupRules(NodeAgentContext context) {
        Instant start = clock.instant();
        double oneMonthSeconds = Duration.ofDays(30).getSeconds();
        Function<Instant, Double> monthNormalizer = instant -> Duration.between(instant, start).getSeconds() / oneMonthSeconds;
        List<DiskCleanupRule> rules = new ArrayList<>();

        rules.add(CoredumpCleanupRule.forContainer(pathOnHostUnderContainerVespaHome(context, "var/crash")));

        if (context.node().membership().map(m -> m.type().isContainer()).orElse(false))
            rules.add(new LinearCleanupRule(() -> FileFinder.files(pathOnHostUnderContainerVespaHome(context, "logs/vespa/qrs")).list(),
                    fa -> monthNormalizer.apply(fa.lastModifiedTime()), Priority.LOWEST, Priority.HIGHEST));

        if (context.nodeType() == NodeType.tenant && context.node().membership().map(m -> m.type().isAdmin()).orElse(false))
            rules.add(new LinearCleanupRule(() -> FileFinder.files(pathOnHostUnderContainerVespaHome(context, "logs/vespa/logarchive")).list(),
                    fa -> monthNormalizer.apply(fa.lastModifiedTime()), Priority.LOWEST, Priority.HIGHEST));

        if (context.nodeType() == NodeType.proxy)
            rules.add(new LinearCleanupRule(() -> FileFinder.files(pathOnHostUnderContainerVespaHome(context, "logs/nginx")).list(),
                    fa -> monthNormalizer.apply(fa.lastModifiedTime()), Priority.LOWEST, Priority.MEDIUM));

        return rules;
    }

    /** Checks if container has any new coredumps, reports and archives them if so */
    public void handleCoreDumpsForContainer(NodeAgentContext context, Optional<Container> container) {
        if (context.isDisabled(NodeAgentTask.CoreDumps)) return;
        coredumpHandler.converge(context, () -> getCoredumpNodeAttributes(context, container));
    }

    private Map<String, Object> getCoredumpNodeAttributes(NodeAgentContext context, Optional<Container> container) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("hostname", context.node().hostname());
        attributes.put("region", context.zone().getRegionName().value());
        attributes.put("environment", context.zone().getEnvironment().value());
        attributes.put("flavor", context.node().flavor());
        attributes.put("kernel_version", System.getProperty("os.version"));
        attributes.put("cpu_microcode_version", getMicrocodeVersion());

        attributes.put("docker_image", getDockerImage(context, container));
        context.node().parentHostname().ifPresent(parent -> attributes.put("parent_hostname", parent));
        context.node().currentVespaVersion().ifPresent(version -> attributes.put("vespa_version", version.toFullString()));
        context.node().owner().ifPresent(owner -> {
            attributes.put("tenant", owner.tenant().value());
            attributes.put("application", owner.application().value());
            attributes.put("instance", owner.instance().value());
        });
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Prepares the container-storage for the next container by deleting/archiving all the data of the current container.
     * Removes old files, reports coredumps and archives container data, runs when container enters state "dirty"
     */
    public void archiveNodeStorage(NodeAgentContext context) {
        Path logsDirInContainer = context.pathInNodeUnderVespaHome("logs");
        Path containerLogsInArchiveDir = archiveContainerStoragePath
                .resolve(context.containerName().asString() + "_" + DATE_TIME_FORMATTER.format(clock.instant()) + logsDirInContainer);
        UnixPath containerLogsOnHost = new UnixPath(context.pathOnHostFromPathInNode(logsDirInContainer));

        if (containerLogsOnHost.exists()) {
            new UnixPath(containerLogsInArchiveDir).createParents();
            containerLogsOnHost.moveIfExists(containerLogsInArchiveDir);
        }
        new UnixPath(context.pathOnHostFromPathInNode("/")).deleteRecursively();
    }

    private String getMicrocodeVersion() {
        String output = uncheck(() -> Files.readAllLines(Paths.get("/proc/cpuinfo")).stream()
                .filter(line -> line.startsWith("microcode"))
                .findFirst()
                .orElseThrow(() -> new ConvergenceException("No microcode information found in /proc/cpuinfo")));

        String[] results = output.split(":");
        if (results.length != 2) {
            throw new ConvergenceException("Result from detect microcode command not as expected: " + output);
        }

        return results[1].trim();
    }

    private String getDockerImage(NodeAgentContext context, Optional<Container> container) {
        return container.map(c -> c.image.asString())
                .orElse(context.node().currentDockerImage()
                        .map(DockerImage::asString)
                        .orElse("<none>")
                );
    }

    private static Path pathOnHostUnderContainerVespaHome(NodeAgentContext context, String path) {
        return context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome(path));
    }
}

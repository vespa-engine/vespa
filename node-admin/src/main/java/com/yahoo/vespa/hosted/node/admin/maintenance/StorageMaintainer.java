// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.maintenance.coredump.CoredumpHandler;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameMatches;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.olderThan;
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
    private final Path archiveContainerStoragePath;
    private final Clock clock;

    // We cache disk usage to avoid doing expensive disk operations so often
    private final Cache<Path, Long> diskUsage = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public StorageMaintainer(Terminal terminal, CoredumpHandler coredumpHandler, Path archiveContainerStoragePath) {
        this(terminal, coredumpHandler, archiveContainerStoragePath, Clock.systemUTC());
    }

    public StorageMaintainer(Terminal terminal, CoredumpHandler coredumpHandler, Path archiveContainerStoragePath, Clock clock) {
        this.terminal = terminal;
        this.coredumpHandler = coredumpHandler;
        this.archiveContainerStoragePath = archiveContainerStoragePath;
        this.clock = clock;
    }

    public Optional<Long> getDiskUsageFor(NodeAgentContext context) {
        try {
            Path path = context.pathOnHostFromPathInNode("/");

            Long cachedDiskUsage = diskUsage.getIfPresent(path);
            if (cachedDiskUsage != null) return Optional.of(cachedDiskUsage);

            long diskUsageBytes = getDiskUsedInBytes(context, path);
            diskUsage.put(path, diskUsageBytes);
            return Optional.of(diskUsageBytes);
        } catch (Exception e) {
            context.log(logger, LogLevel.WARNING, "Failed to get disk usage", e);
            return Optional.empty();
        }
    }

    // Public for testing
    long getDiskUsedInBytes(TaskContext context, Path path) {
        if (!Files.exists(path)) return 0;

        String output = terminal.newCommandLine(context)
                .add("du", "-xsk", path.toString())
                .setTimeout(Duration.ofSeconds(60))
                .executeSilently()
                .getOutput();

        String[] results = output.split("\t");
        if (results.length != 2) {
            throw new RuntimeException("Result from disk usage command not as expected: " + output);
        }

        return 1024 * Long.parseLong(results[0]);
    }


    /** Deletes old log files for vespa, nginx, logstash, etc. */
    public void removeOldFilesFromNode(NodeAgentContext context) {
        Path[] logPaths = {
                context.pathInNodeUnderVespaHome("logs/daemontools_y"),
                context.pathInNodeUnderVespaHome("logs/nginx"),
                context.pathInNodeUnderVespaHome("logs/vespa")
        };

        for (Path pathToClean : logPaths) {
            Path path = context.pathOnHostFromPathInNode(pathToClean);
            FileFinder.files(path)
                    .match(olderThan(Duration.ofDays(3)).and(nameMatches(Pattern.compile(".*\\.log.+"))))
                    .maxDepth(1)
                    .deleteRecursively(context);
        }

        FileFinder.files(context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome("logs/vespa/qrs")))
                .match(olderThan(Duration.ofDays(3)))
                .deleteRecursively(context);

        FileFinder.files(context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome("logs/vespa/logarchive")))
                .match(olderThan(Duration.ofDays(31)))
                .deleteRecursively(context);

        FileFinder.directories(context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome("var/db/vespa/filedistribution")))
                .match(olderThan(Duration.ofDays(31)))
                .deleteRecursively(context);

        FileFinder.directories(context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome("var/db/vespa/download")))
                .match(olderThan(Duration.ofDays(31)))
                .deleteRecursively(context);
    }

    /** Checks if container has any new coredumps, reports and archives them if so */
    public void handleCoreDumpsForContainer(NodeAgentContext context, Optional<Container> container) {
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

        container.map(c -> c.image).ifPresent(image -> attributes.put("docker_image", image.asString()));
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
                .orElseThrow(() -> new RuntimeException("No microcode information found in /proc/cpuinfo")));

        String[] results = output.split(":");
        if (results.length != 2) {
            throw new RuntimeException("Result from detect microcode command not as expected: " + output);
        }

        return results[1].trim();
    }
}

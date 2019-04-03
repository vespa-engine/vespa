// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.yahoo.config.provision.NodeType;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.coredump.CoredumpHandler;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;
import com.yahoo.vespa.hosted.node.admin.util.SecretAgentCheckConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameMatches;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.olderThan;
import static com.yahoo.vespa.hosted.node.admin.util.SecretAgentCheckConfig.nodeTypeToRole;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author freva
 */
public class StorageMaintainer {
    private static final Logger logger = Logger.getLogger(StorageMaintainer.class.getName());
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final Terminal terminal;
    private final DockerOperations dockerOperations;
    private final CoredumpHandler coredumpHandler;
    private final Path archiveContainerStoragePath;

    // We cache disk usage to avoid doing expensive disk operations so often
    private final Cache<Path, Long> diskUsage = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public StorageMaintainer(Terminal terminal, DockerOperations dockerOperations, CoredumpHandler coredumpHandler, Path archiveContainerStoragePath) {
        this.terminal = terminal;
        this.dockerOperations = dockerOperations;
        this.coredumpHandler = coredumpHandler;
        this.archiveContainerStoragePath = archiveContainerStoragePath;
    }

    public void writeMetricsConfig(NodeAgentContext context) {
        List<SecretAgentCheckConfig> configs = new ArrayList<>();
        Map<String, Object> tags = generateTags(context);

        // host-life
        Path hostLifeCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_host_life");
        configs.add(new SecretAgentCheckConfig("host-life", 60, hostLifeCheckPath).withTags(tags));

        // coredumps (except for the done coredumps which is handled by the host)
        Path coredumpCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_coredumps");
        configs.add(new SecretAgentCheckConfig("system-coredumps-processing", 300, coredumpCheckPath,
                "--application", "system-coredumps-processing",
                "--lastmin", "129600",
                "--crit", "1",
                "--coredir", context.pathInNodeUnderVespaHome("var/crash/processing").toString())
                .withTags(tags));

        // athenz certificate check
        Path athenzCertExpiryCheckPath = context.pathInNodeUnderVespaHome("libexec64/yms/yms_check_athenz_certs");
        configs.add(new SecretAgentCheckConfig("athenz-certificate-expiry", 60, athenzCertExpiryCheckPath,
                "--threshold", "20")
                .withRunAsUser("root")
                .withTags(tags));

        if (context.nodeType() != NodeType.config) {
            // vespa-health
            Path vespaHealthCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_vespa_health");
            configs.add(new SecretAgentCheckConfig("vespa-health", 60, vespaHealthCheckPath, "all")
                    .withRunAsUser(context.vespaUser())
                    .withTags(tags));

            // vespa
            Path vespaCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_vespa");
            SecretAgentCheckConfig vespaSchedule = new SecretAgentCheckConfig("vespa", 60, vespaCheckPath, "all");
            vespaSchedule.withRunAsUser(context.vespaUser());
            if (isConfigserverLike(context.nodeType())) {
                Map<String, Object> tagsWithoutNameSpace = new LinkedHashMap<>(tags);
                tagsWithoutNameSpace.remove("namespace");
                vespaSchedule.withTags(tagsWithoutNameSpace);
            }
            configs.add(vespaSchedule);
        }

        if (context.nodeType() == NodeType.config || context.nodeType() == NodeType.controller) {

            // configserver/controller
            Path configServerNewCheckPath = Paths.get("/usr/bin/curl");
            configs.add(new SecretAgentCheckConfig(nodeTypeToRole(context.nodeType()), 60, configServerNewCheckPath,
                                                   "-s", "localhost:19071/yamas-metrics")
                                .withTags(tags));

            //zkbackupage
            Path zkbackupCheckPath = context.pathInNodeUnderVespaHome("libexec/yamas2/yms_check_file_age.py");
            configs.add(new SecretAgentCheckConfig("zkbackupage", 300, zkbackupCheckPath,
                    "-f", context.pathInNodeUnderVespaHome("var/vespa-hosted/zkbackup.stat").toString(),
                    "-m", "150",
                    "-a", "config-zkbackupage")
                    .withTags(tags));

	    String appName = nodeTypeToRole(context.nodeType()) + "-logd";
            Path logdCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/convert-state-metrics-2-yamas.py");
            configs.add(new SecretAgentCheckConfig(appName, 60, logdCheckPath,
                    appName, "http://localhost:19089/state/v1/metrics")
                    .withTags(tags));
        }

        if (context.nodeType() == NodeType.proxy) {
            //routing-configage
            Path routingAgeCheckPath = context.pathInNodeUnderVespaHome("libexec/yamas2/yms_check_file_age.py");
            configs.add(new SecretAgentCheckConfig("routing-configage", 60, routingAgeCheckPath,
                    "-f", context.pathInNodeUnderVespaHome("var/vespa-hosted/routing/nginx.conf.tmp").toString(),
                    "-m", "1",
                    "-a", "routing-configage",
                    "--ignore_file_not_found")
                    .withTags(tags));

            //ssl-check
            Path sslCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_ssl_status");
            configs.add(new SecretAgentCheckConfig("ssl-status", 300, sslCheckPath,
                    "-e", "localhost",
                    "-p", "4443",
                    "-t", "30")
                    .withTags(tags));
        }

        // Write config and restart yamas-agent
        Path yamasAgentFolder = context.pathOnHostFromPathInNode("/etc/yamas-agent");
        configs.forEach(s -> uncheck(() -> s.writeTo(yamasAgentFolder)));
        dockerOperations.executeCommandInContainerAsRoot(context, "service", "yamas-agent", "restart");
    }

    private Map<String, Object> generateTags(NodeAgentContext context) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("namespace", "Vespa");
        tags.put("role", nodeTypeToRole(context.node().getNodeType()));
        tags.put("zone", String.format("%s.%s", context.zoneId().environment().value(), context.zoneId().regionName().value()));
        context.node().getVespaVersion().ifPresent(version -> tags.put("vespaVersion", version.toFullString()));

        if (! isConfigserverLike(context.nodeType())) {
            tags.put("flavor", context.node().getFlavor());
            tags.put("canonicalFlavor", context.node().getCanonicalFlavor());
            tags.put("state", context.node().getState().toString());
            context.node().getParentHostname().ifPresent(parent -> tags.put("parentHostname", parent));
            context.node().getOwner().ifPresent(owner -> {
                tags.put("tenantName", owner.getTenant());
                tags.put("app", owner.getApplication() + "." + owner.getInstance());
                tags.put("applicationName", owner.getApplication());
                tags.put("instanceName", owner.getInstance());
                tags.put("applicationId", owner.getTenant() + "." + owner.getApplication() + "." + owner.getInstance());
            });
            context.node().getMembership().ifPresent(membership -> {
                tags.put("clustertype", membership.getClusterType());
                tags.put("clusterid", membership.getClusterId());
            });
        }

        return Collections.unmodifiableMap(tags);
    }

    private boolean isConfigserverLike(NodeType nodeType) {
        return nodeType == NodeType.config || nodeType == NodeType.controller;
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

        return 1024 * Long.valueOf(results[0]);
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
                    .deleteRecursively();
        }

        FileFinder.files(context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome("logs/vespa/qrs")))
                .match(olderThan(Duration.ofDays(3)))
                .deleteRecursively();

        FileFinder.files(context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome("logs/vespa/logarchive")))
                .match(olderThan(Duration.ofDays(31)))
                .deleteRecursively();

        FileFinder.directories(context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome("var/db/vespa/filedistribution")))
                .match(olderThan(Duration.ofDays(31)))
                .deleteRecursively();

        FileFinder.directories(context.pathOnHostFromPathInNode(context.pathInNodeUnderVespaHome("var/db/vespa/download")))
                .match(olderThan(Duration.ofDays(31)))
                .deleteRecursively();
    }

    /** Checks if container has any new coredumps, reports and archives them if so */
    public void handleCoreDumpsForContainer(NodeAgentContext context, Optional<Container> container) {
        coredumpHandler.converge(context, () -> getCoredumpNodeAttributes(context, container));
    }

    private Map<String, Object> getCoredumpNodeAttributes(NodeAgentContext context, Optional<Container> container) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("hostname", context.node().getHostname());
        attributes.put("region", context.zoneId().regionName().value());
        attributes.put("environment", context.zoneId().environment().value());
        attributes.put("flavor", context.node().getFlavor());
        attributes.put("kernel_version", System.getProperty("os.version"));
        attributes.put("cpu_microcode_version", getMicrocodeVersion());

        container.map(c -> c.image).ifPresent(image -> attributes.put("docker_image", image.asString()));
        context.node().getParentHostname().ifPresent(parent -> attributes.put("parent_hostname", parent));
        context.node().getVespaVersion().ifPresent(version -> attributes.put("vespa_version", version.toFullString()));
        context.node().getOwner().ifPresent(owner -> {
            attributes.put("tenant", owner.getTenant());
            attributes.put("application", owner.getApplication());
            attributes.put("instance", owner.getInstance());
        });
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Prepares the container-storage for the next container by deleting/archiving all the data of the current container.
     * Removes old files, reports coredumps and archives container data, runs when container enters state "dirty"
     */
    public void archiveNodeStorage(NodeAgentContext context) {
        Path logsDirInContainer = context.pathInNodeUnderVespaHome("logs");
        Path containerLogsOnHost = context.pathOnHostFromPathInNode(logsDirInContainer);
        Path containerLogsInArchiveDir = archiveContainerStoragePath
                .resolve(context.containerName().asString() + "_" + DATE_TIME_FORMATTER.format(Instant.now()) + logsDirInContainer);

        new UnixPath(containerLogsInArchiveDir).createParents();
        new UnixPath(containerLogsOnHost).moveIfExists(containerLogsInArchiveDir);
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

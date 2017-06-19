// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.collections.Pair;
import com.yahoo.io.IOUtils;
import com.yahoo.net.HostName;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.dockerapi.metrics.CounterWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.logging.FilebeatConfigProvider;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.node.admin.util.SecretAgentScheduleMaker;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * @author freva
 */
public class StorageMaintainer {
    private static final ContainerName NODE_ADMIN = new ContainerName("node-admin");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Optional<String> kernelVersion = Optional.empty();

    private static final long intervalSec = 1000;

    private final Object monitor = new Object();
    private final CounterWrapper numberOfNodeAdminMaintenanceFails;
    private final Docker docker;
    private final Environment environment;
    private final Clock clock;

    private Map<ContainerName, MaintenanceThrottler> maintenanceThrottlerByContainerName = new ConcurrentHashMap<>();


    public StorageMaintainer(Docker docker, MetricReceiverWrapper metricReceiver, Environment environment, Clock clock) {
        this.docker = docker;
        this.environment = environment;
        this.clock = clock;

        Dimensions dimensions = new Dimensions.Builder().add("role", "docker").build();
        numberOfNodeAdminMaintenanceFails = metricReceiver.declareCounter(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.maintenance.fails");
    }

    public void writeMetricsConfig(ContainerName containerName, ContainerNodeSpec nodeSpec) {
        final Path yamasAgentFolder = environment.pathInNodeAdminFromPathInNode(containerName, "/etc/yamas-agent/");

        Path vespaCheckPath = Paths.get(getDefaults().underVespaHome("libexec/yms/yms_check_vespa"));
        SecretAgentScheduleMaker vespaSchedule = new SecretAgentScheduleMaker("vespa", 60, vespaCheckPath, "all")
                .withTag("parentHostname", environment.getParentHostHostname());

        Path hostLifeCheckPath = Paths.get("/home/y/libexec/yms/yms_check_host_life");
        SecretAgentScheduleMaker hostLifeSchedule = new SecretAgentScheduleMaker("host-life", 60, hostLifeCheckPath)
                .withTag("namespace", "Vespa")
                .withTag("role", "tenants")
                .withTag("flavor", nodeSpec.nodeFlavor)
                .withTag("state", nodeSpec.nodeState.toString())
                .withTag("zone", environment.getZone())
                .withTag("parentHostname", environment.getParentHostHostname());
        nodeSpec.owner.ifPresent(owner -> hostLifeSchedule
                .withTag("tenantName", owner.tenant)
                .withTag("app", owner.application + "." + owner.instance)
                .withTag("applicationName", owner.application)
                .withTag("instanceName", owner.instance)
                .withTag("applicationId", owner.tenant + "." + owner.application + "." + owner.instance));
        nodeSpec.membership.ifPresent(membership -> hostLifeSchedule
                .withTag("clustertype", membership.clusterType)
                .withTag("clusterid", membership.clusterId));
        nodeSpec.vespaVersion.ifPresent(version -> hostLifeSchedule.withTag("vespaVersion", version));

        try {
            vespaSchedule.writeTo(yamasAgentFolder);
            hostLifeSchedule.writeTo(yamasAgentFolder);
            final String[] restartYamasAgent = new String[]{"service", "yamas-agent", "restart"};
            docker.executeInContainerAsRoot(containerName, restartYamasAgent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write secret-agent schedules for " + containerName, e);
        }
    }

    public void writeFilebeatConfig(ContainerName containerName, ContainerNodeSpec nodeSpec) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(StorageMaintainer.class, containerName);
        try {
            FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(environment);
            Optional<String> config = filebeatConfigProvider.getConfig(nodeSpec);
            if (!config.isPresent()) {
                logger.error("Was not able to generate a config for filebeat, ignoring filebeat file creation." + nodeSpec.toString());
                return;
            }
            Path filebeatPath = environment.pathInNodeAdminFromPathInNode(containerName, "/etc/filebeat/filebeat.yml");
            Files.write(filebeatPath, config.get().getBytes());
            logger.info("Wrote filebeat config.");
        } catch (Throwable t) {
            logger.error("Failed writing filebeat config; " + nodeSpec, t);
        }
    }

    public Optional<Long> updateIfNeededAndGetDiskMetricsFor(ContainerName containerName) {
        // Calculating disk usage is IO expensive operation and its value changes relatively slowly, we want to perform
        // that calculation rarely. Additionally, we spread out the calculation for different containers by adding
        // a random deviation.
        MaintenanceThrottler maintenanceThrottler = getMaintenanceThrottlerFor(containerName);
        if (maintenanceThrottler.shouldUpdateDiskUsageNow()) {
            // Throttle to one disk usage calculation at a time.
            synchronized (monitor) {
                PrefixLogger logger = PrefixLogger.getNodeAgentLogger(StorageMaintainer.class, containerName);
                Path containerDir = environment.pathInNodeAdminFromPathInNode(containerName, "/home/");
                try {
                    long used = getDiscUsedInBytes(containerDir);
                    maintenanceThrottler.setDiskUsage(used);
                } catch (Throwable e) {
                    logger.error("Problems during disk usage calculations: " + e.getMessage());
                }
            }
        }

        return maintenanceThrottler.diskUsage;
    }

    // Public for testing
    long getDiscUsedInBytes(Path path) throws IOException, InterruptedException {
        final String[] command = {"du", "-xsk", path.toString()};

        Process duCommand = new ProcessBuilder().command(command).start();
        if (!duCommand.waitFor(60, TimeUnit.SECONDS)) {
            duCommand.destroy();
            throw new RuntimeException("Disk usage command timedout, aborting.");
        }
        String output = IOUtils.readAll(new InputStreamReader(duCommand.getInputStream()));
        String error = IOUtils.readAll(new InputStreamReader(duCommand.getErrorStream()));

        if (! error.isEmpty()) {
            throw new RuntimeException("Disk usage wrote to error log: " + error);
        }

        String[] results = output.split("\t");
        if (results.length != 2) {
            throw new RuntimeException("Result from disk usage command not as expected: " + output);
        }

        long diskUsageKB = Long.valueOf(results[0]);
        return diskUsageKB * 1024;
    }


    /**
     * Deletes old log files for vespa, nginx, logstash, etc.
     */
    public void removeOldFilesFromNode(ContainerName containerName) {
        if (! getMaintenanceThrottlerFor(containerName).shouldRemoveOldFilesNow()) return;

        MaintainerExecutor maintainerExecutor = new MaintainerExecutor();
        String[] pathsToClean = {"/home/y/logs/elasticsearch2", "/home/y/logs/logstash2",
                "/home/y/logs/daemontools_y", "/home/y/logs/nginx", "/home/y/logs/vespa"};

        for (String pathToClean : pathsToClean) {
            Path path = environment.pathInNodeAdminFromPathInNode(containerName, pathToClean);
            if (Files.exists(path)) {
                maintainerExecutor.addJob("delete-files")
                        .withArgument("basePath", path)
                        .withArgument("maxAgeSeconds", Duration.ofDays(3).getSeconds())
                        .withArgument("fileNameRegex", ".*\\.log\\..+")
                        .withArgument("recursive", false);

                maintainerExecutor.addJob("delete-files")
                        .withArgument("basePath", path)
                        .withArgument("maxAgeSeconds", Duration.ofDays(3).getSeconds())
                        .withArgument("fileNameRegex", ".*QueryAccessLog.*")
                        .withArgument("recursive", false);
            }
        }

        Path logArchiveDir = environment.pathInNodeAdminFromPathInNode(containerName, "/home/y/logs/vespa/logarchive");
        maintainerExecutor.addJob("delete-files")
                .withArgument("basePath", logArchiveDir)
                .withArgument("maxAgeSeconds", Duration.ofDays(31).getSeconds())
                .withArgument("recursive", false);

        Path fileDistrDir = environment.pathInNodeAdminFromPathInNode(containerName, "/home/y/var/db/vespa/filedistribution");
        maintainerExecutor.addJob("delete-files")
                .withArgument("basePath", fileDistrDir)
                .withArgument("maxAgeSeconds", Duration.ofDays(31).getSeconds())
                .withArgument("recursive", true);

        maintainerExecutor.execute();
        getMaintenanceThrottlerFor(containerName).updateNextRemoveOldFilesTime();
    }

    /**
     * Checks if container has any new coredumps, reports and archives them if so
     */
    public void handleCoreDumpsForContainer(ContainerName containerName, ContainerNodeSpec nodeSpec, Environment environment) {
        if (! getMaintenanceThrottlerFor(containerName).shouldHandleCoredumpsNow()) return;

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("hostname", nodeSpec.hostname);
        attributes.put("parent_hostname", HostName.getLocalhost());
        attributes.put("region", environment.getRegion());
        attributes.put("environment", environment.getEnvironment());
        attributes.put("flavor", nodeSpec.nodeFlavor);
        try {
            attributes.put("kernel_version", getKernelVersion());
        } catch (Throwable ignored) {
            attributes.put("kernel_version", "unknown");
        }

        nodeSpec.wantedDockerImage.ifPresent(image -> attributes.put("docker_image", image.asString()));
        nodeSpec.vespaVersion.ifPresent(version -> attributes.put("vespa_version", version));
        nodeSpec.owner.ifPresent(owner -> {
            attributes.put("tenant", owner.tenant);
            attributes.put("application", owner.application);
            attributes.put("instance", owner.instance);
        });

        MaintainerExecutor maintainerExecutor = new MaintainerExecutor();
        maintainerExecutor.addJob("handle-core-dumps")
                .withArgument("doneCoredumpsPath", environment.pathInNodeAdminToDoneCoredumps())
                .withArgument("coredumpsPath", environment.pathInNodeAdminFromPathInNode(containerName, "/home/y/var/crash"))
                .withArgument("feedEndpoint", environment.getCoredumpFeedEndpoint())
                .withArgument("attributes", attributes);

        maintainerExecutor.execute();
        getMaintenanceThrottlerFor(containerName).updateNextHandleCoredumpsTime();
    }

    /**
     * Deletes old
     *  * archived app data
     *  * Vespa logs
     *  * Filedistribution files
     */
    public void cleanNodeAdmin() {
        if (! getMaintenanceThrottlerFor(NODE_ADMIN).shouldRemoveOldFilesNow()) return;

        MaintainerExecutor maintainerExecutor = new MaintainerExecutor();
        maintainerExecutor.addJob("delete-directories")
                .withArgument("basePath", environment.getPathResolver().getApplicationStoragePathForNodeAdmin())
                .withArgument("maxAgeSeconds", Duration.ofDays(7).getSeconds())
                .withArgument("dirNameRegex", "^" + Pattern.quote(Environment.APPLICATION_STORAGE_CLEANUP_PATH_PREFIX));

        Path nodeAdminJDiskLogsPath = environment.pathInNodeAdminFromPathInNode(NODE_ADMIN, "/home/y/logs/vespa/");
        maintainerExecutor.addJob("delete-files")
                .withArgument("basePath", nodeAdminJDiskLogsPath)
                .withArgument("maxAgeSeconds", Duration.ofDays(31).getSeconds())
                .withArgument("recursive", false);

        Path fileDistrDir = environment.pathInNodeAdminFromPathInNode(NODE_ADMIN, "/home/y/var/db/vespa/filedistribution");
        maintainerExecutor.addJob("delete-files")
                .withArgument("basePath", fileDistrDir)
                .withArgument("maxAgeSeconds", Duration.ofDays(31).getSeconds())
                .withArgument("recursive", true);

        maintainerExecutor.execute();
        getMaintenanceThrottlerFor(NODE_ADMIN).updateNextRemoveOldFilesTime();
    }

    /**
     * Archives container data, runs when container enters state "dirty"
     */
    public void archiveNodeData(ContainerName containerName) {
        MaintainerExecutor maintainerExecutor = new MaintainerExecutor();
        maintainerExecutor.addJob("recursive-delete")
                .withArgument("path", environment.pathInNodeAdminFromPathInNode(containerName, "/home/y/var"));

        maintainerExecutor.addJob("move-files")
                .withArgument("from", environment.pathInNodeAdminFromPathInNode(containerName, "/"))
                .withArgument("to", environment.pathInNodeAdminToNodeCleanup(containerName));

        maintainerExecutor.execute();
        getMaintenanceThrottlerFor(containerName).reset();
    }



    private String getKernelVersion() throws IOException, InterruptedException {
        if (! kernelVersion.isPresent()) {
            Pair<Integer, String> result = new ProcessExecuter().exec(new String[]{"uname", "-r"});
            if (result.getFirst() == 0) {
                kernelVersion = Optional.of(result.getSecond().trim());
            } else {
                throw new RuntimeException("Failed to get kernel version\n" + result);
            }
        }

        return kernelVersion.orElse("unknown");
    }

    /**
     * Wrapper for node-admin-maintenance, queues up maintenances jobs and sends a single request to maintenance JVM
     */
    private class MaintainerExecutor {
        private final List<MaintainerExecutorJob> jobs = new ArrayList<>();
        private final ContainerName executeIn;

        MaintainerExecutor(ContainerName executeIn) {
            this.executeIn = executeIn;
        }

        MaintainerExecutor() {
            this(NODE_ADMIN);
        }

        MaintainerExecutorJob addJob(String jobName) {
            MaintainerExecutorJob job = new MaintainerExecutorJob(jobName);
            jobs.add(job);
            return job;
        }

        void execute() {
            String args;
            try {
                args = objectMapper.writeValueAsString(jobs);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed transform list of maintenance jobs to JSON");
            }

            String[] command = {"java",
                    "-cp", "/home/y/lib/jars/node-maintainer-jar-with-dependencies.jar",
                    "-Dvespa.log.target=file:" + getDefaults().underVespaHome("logs/vespa/maintainer.log"),
                    "com.yahoo.vespa.hosted.node.maintainer.Maintainer", args};
            ProcessResult result = docker.executeInContainerAsRoot(executeIn, command);

            if (! result.isSuccess()) {
                numberOfNodeAdminMaintenanceFails.add();
                throw new RuntimeException("Failed to run maintenance jobs: " + args + result);
            }
        }
    }

    private class MaintainerExecutorJob {
        @JsonProperty(value="type")
        private final String type;

        @JsonProperty(value="arguments")
        private final Map<String, Object> arguments = new HashMap<>();

        MaintainerExecutorJob(String type) {
            this.type = type;
        }

        MaintainerExecutorJob withArgument(String argument, Object value) {
            // Transform Path to String, otherwise ObjectMapper wont encode/decode it properly on the other end
            arguments.put(argument, (value instanceof Path) ? value.toString() : value);
            return this;
        }
    }

    private MaintenanceThrottler getMaintenanceThrottlerFor(ContainerName containerName) {
        if (! maintenanceThrottlerByContainerName.containsKey(containerName)) {
            maintenanceThrottlerByContainerName.put(containerName, new MaintenanceThrottler());
        }

        return maintenanceThrottlerByContainerName.get(containerName);
    }

    private class MaintenanceThrottler {
        private Instant nextDiskUsageUpdateAt;
        private Instant nextRemoveOldFilesAt;
        private Instant nextHandleOldCoredumpsAt;
        private Optional<Long> diskUsage = Optional.empty();

        MaintenanceThrottler() {
            reset();
        }

        boolean shouldUpdateDiskUsageNow() {
            return !nextDiskUsageUpdateAt.isAfter(clock.instant());
        }

        void setDiskUsage(long diskUsage) {
            this.diskUsage = Optional.of(diskUsage);
            long distributedSecs = (long) (intervalSec * (0.5 + Math.random()));
            nextDiskUsageUpdateAt = clock.instant().plusSeconds(distributedSecs);
        }

        void updateNextRemoveOldFilesTime() {
            nextRemoveOldFilesAt = clock.instant().plus(Duration.ofHours(1));
        }

        boolean shouldRemoveOldFilesNow() {
            return !nextRemoveOldFilesAt.isAfter(clock.instant());
        }

        void updateNextHandleCoredumpsTime() {
            nextHandleOldCoredumpsAt = clock.instant().plus(Duration.ofHours(1));
        }

        boolean shouldHandleCoredumpsNow() {
            return !nextHandleOldCoredumpsAt.isAfter(clock.instant());
        }

        void reset() {
            nextDiskUsageUpdateAt = clock.instant().minus(Duration.ofDays(1));
            nextRemoveOldFilesAt = clock.instant().minus(Duration.ofDays(1));
            nextHandleOldCoredumpsAt = clock.instant().minus(Duration.ofDays(1));
        }
    }
}

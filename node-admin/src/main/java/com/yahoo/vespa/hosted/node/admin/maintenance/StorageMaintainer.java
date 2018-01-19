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
import com.yahoo.vespa.hosted.dockerapi.metrics.CounterWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * @author freva
 */
public class StorageMaintainer {
    private static final ContainerName NODE_ADMIN = new ContainerName("node-admin");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final CounterWrapper numberOfNodeAdminMaintenanceFails;
    private final DockerOperations dockerOperations;
    private final ProcessExecuter processExecuter;
    private final Environment environment;
    private final Clock clock;

    private Map<ContainerName, MaintenanceThrottler> maintenanceThrottlerByContainerName = new ConcurrentHashMap<>();


    public StorageMaintainer(DockerOperations dockerOperations, ProcessExecuter processExecuter, MetricReceiverWrapper metricReceiver, Environment environment, Clock clock) {
        this.dockerOperations = dockerOperations;
        this.processExecuter = processExecuter;
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

        Path hostLifeCheckPath = Paths.get(getDefaults().underVespaHome("libexec/yms/yms_check_host_life"));
        SecretAgentScheduleMaker hostLifeSchedule = new SecretAgentScheduleMaker("host-life", 60, hostLifeCheckPath)
                .withTag("namespace", "Vespa")
                .withTag("role", "tenants")
                .withTag("flavor", nodeSpec.nodeFlavor)
                .withTag("canonicalFlavor", nodeSpec.nodeCanonicalFlavor)
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
            dockerOperations.executeCommandInContainerAsRoot(containerName, restartYamasAgent);
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

    public Optional<Long> getDiskUsageFor(ContainerName containerName) {
        Path containerDir = environment.pathInNodeAdminFromPathInNode(containerName, "/home/");
        try {
            return Optional.of(getDiskUsedInBytes(containerDir));
        } catch (Throwable e) {
            PrefixLogger logger = PrefixLogger.getNodeAgentLogger(StorageMaintainer.class, containerName);
            logger.error("Problems during disk usage calculations in " + containerDir.toAbsolutePath(), e);
            return Optional.empty();
        }
    }

    // Public for testing
    long getDiskUsedInBytes(Path path) throws IOException, InterruptedException {
        final String[] command = {"du", "-xsk", path.toString()};

        Process duCommand = new ProcessBuilder().command(command).start();
        if (!duCommand.waitFor(60, TimeUnit.SECONDS)) {
            duCommand.destroy();
            throw new RuntimeException("Disk usage command timed out, aborting.");
        }
        String output = IOUtils.readAll(new InputStreamReader(duCommand.getInputStream()));
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
        addRemoveOldFilesCommand(maintainerExecutor, containerName);

        maintainerExecutor.execute();
        getMaintenanceThrottlerFor(containerName).updateNextRemoveOldFilesTime();
    }

    private void addRemoveOldFilesCommand(MaintainerExecutor maintainerExecutor, ContainerName containerName) {
        String[] pathsToClean = {
                getDefaults().underVespaHome("logs/elasticsearch2"),
                getDefaults().underVespaHome("logs/logstash2"),
                getDefaults().underVespaHome("logs/daemontools_y"),
                getDefaults().underVespaHome("logs/nginx"),
                getDefaults().underVespaHome("logs/vespa")
        };

        for (String pathToClean : pathsToClean) {
            Path path = environment.pathInNodeAdminFromPathInNode(containerName, pathToClean);
            if (Files.exists(path)) {
                maintainerExecutor.addJob("delete-files")
                        .withArgument("basePath", path)
                        .withArgument("maxAgeSeconds", Duration.ofDays(3).getSeconds())
                        .withArgument("fileNameRegex", ".*\\.log.+")
                        .withArgument("recursive", false);
            }
        }

        Path qrsDir = environment.pathInNodeAdminFromPathInNode(
                containerName, getDefaults().underVespaHome("logs/vespa/qrs"));
        maintainerExecutor.addJob("delete-files")
                .withArgument("basePath", qrsDir)
                .withArgument("maxAgeSeconds", Duration.ofDays(3).getSeconds())
                .withArgument("fileNameRegex", ".*QueryAccessLog.*")
                .withArgument("recursive", false);

        Path logArchiveDir = environment.pathInNodeAdminFromPathInNode(
                containerName, getDefaults().underVespaHome("logs/vespa/logarchive"));
        maintainerExecutor.addJob("delete-files")
                .withArgument("basePath", logArchiveDir)
                .withArgument("maxAgeSeconds", Duration.ofDays(31).getSeconds())
                .withArgument("recursive", false);

        Path fileDistrDir = environment.pathInNodeAdminFromPathInNode(
                containerName, getDefaults().underVespaHome("var/db/vespa/filedistribution"));
        maintainerExecutor.addJob("delete-files")
                .withArgument("basePath", fileDistrDir)
                .withArgument("maxAgeSeconds", Duration.ofDays(31).getSeconds())
                .withArgument("recursive", true);
    }

    /**
     * Checks if container has any new coredumps, reports and archives them if so
     *
     * @param force Set to true to bypass throttling
     */
    public void handleCoreDumpsForContainer(ContainerName containerName, ContainerNodeSpec nodeSpec, boolean force) {
        if (! getMaintenanceThrottlerFor(containerName).shouldHandleCoredumpsNow() && !force) return;

        MaintainerExecutor maintainerExecutor = new MaintainerExecutor();
        addHandleCoredumpsCommand(maintainerExecutor, containerName, nodeSpec);

        maintainerExecutor.execute();
        getMaintenanceThrottlerFor(containerName).updateNextHandleCoredumpsTime();
    }

    private void addHandleCoredumpsCommand(MaintainerExecutor maintainerExecutor, ContainerName containerName, ContainerNodeSpec nodeSpec) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("hostname", nodeSpec.hostname);
        attributes.put("parent_hostname", HostName.getLocalhost());
        attributes.put("region", environment.getRegion());
        attributes.put("environment", environment.getEnvironment());
        attributes.put("flavor", nodeSpec.nodeFlavor);
        attributes.put("kernel_version", System.getProperty("os.version"));

        nodeSpec.currentDockerImage.ifPresent(image -> attributes.put("docker_image", image.asString()));
        nodeSpec.vespaVersion.ifPresent(version -> attributes.put("vespa_version", version));
        nodeSpec.owner.ifPresent(owner -> {
            attributes.put("tenant", owner.tenant);
            attributes.put("application", owner.application);
            attributes.put("instance", owner.instance);
        });

        maintainerExecutor.addJob("handle-core-dumps")
                .withArgument("doneCoredumpsPath", environment.pathInNodeAdminToDoneCoredumps())
                .withArgument("coredumpsPath", environment.pathInNodeAdminFromPathInNode(
                        containerName, getDefaults().underVespaHome("var/crash")))
                .withArgument("feedEndpoint", environment.getCoredumpFeedEndpoint())
                .withArgument("attributes", attributes);
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

        Path nodeAdminJDiskLogsPath = environment.pathInNodeAdminFromPathInNode(
                NODE_ADMIN, getDefaults().underVespaHome("logs/vespa/"));
        maintainerExecutor.addJob("delete-files")
                .withArgument("basePath", nodeAdminJDiskLogsPath)
                .withArgument("maxAgeSeconds", Duration.ofDays(31).getSeconds())
                .withArgument("recursive", false);

        Path fileDistrDir = environment.pathInNodeAdminFromPathInNode(
                NODE_ADMIN, getDefaults().underVespaHome("var/db/vespa/filedistribution"));
        maintainerExecutor.addJob("delete-files")
                .withArgument("basePath", fileDistrDir)
                .withArgument("maxAgeSeconds", Duration.ofDays(31).getSeconds())
                .withArgument("recursive", true);

        maintainerExecutor.execute();
        getMaintenanceThrottlerFor(NODE_ADMIN).updateNextRemoveOldFilesTime();
    }

    /**
     * Prepares the container-storage for the next container by deleting/archiving all the data of the current container.
     * Removes old files, reports coredumps and archives container data, runs when container enters state "dirty"
     */
    public void cleanupNodeStorage(ContainerName containerName, ContainerNodeSpec nodeSpec) {
        MaintainerExecutor maintainerExecutor = new MaintainerExecutor();
        addRemoveOldFilesCommand(maintainerExecutor, containerName);
        addHandleCoredumpsCommand(maintainerExecutor, containerName, nodeSpec);
        addArchiveNodeData(maintainerExecutor, containerName);

        maintainerExecutor.execute();
        getMaintenanceThrottlerFor(containerName).reset();
    }

    private void addArchiveNodeData(MaintainerExecutor maintainerExecutor, ContainerName containerName) {
        maintainerExecutor.addJob("recursive-delete")
                .withArgument("path", environment.pathInNodeAdminFromPathInNode(
                        containerName, getDefaults().underVespaHome("var")));

        maintainerExecutor.addJob("move-files")
                .withArgument("from", environment.pathInNodeAdminFromPathInNode(containerName, "/"))
                .withArgument("to", environment.pathInNodeAdminToNodeCleanup(containerName));
    }

    /**
     * Runs node-maintainer's SpecVerifier and returns its output
     * @param nodeSpec Node specification containing the excepted values we want to verify against
     * @return new combined hardware divergence
     * @throws RuntimeException if exit code != 0
     */
    public String getHardwareDivergence(ContainerNodeSpec nodeSpec) {
        List<String> arguments = new ArrayList<>(Arrays.asList("specification",
                "--disk", Double.toString(nodeSpec.minDiskAvailableGb),
                "--memory", Double.toString(nodeSpec.minMainMemoryAvailableGb),
                "--cpu_cores", Double.toString(nodeSpec.minCpuCores),
                "--is_ssd", Boolean.toString(nodeSpec.fastDisk),
                "--ips", String.join(",", nodeSpec.ipAddresses)));

        if (nodeSpec.hardwareDivergence.isPresent()) {
            arguments.add("--divergence");
            arguments.add(nodeSpec.hardwareDivergence.get());
        }

        return executeMaintainer("com.yahoo.vespa.hosted.node.verification.Main", arguments.toArray(new String[0]));
    }


    private String executeMaintainer(String mainClass, String... args) {
        String[] command = Stream.concat(
                Stream.of("sudo",
                        "VESPA_HOME=" + getDefaults().vespaHome(),
                        getDefaults().underVespaHome("libexec/vespa/node-admin/maintenance.sh"),
                        mainClass),
                Stream.of(args))
                .toArray(String[]::new);

        try {
            Pair<Integer, String> result = processExecuter.exec(command);

            if (result.getFirst() != 0) {
                numberOfNodeAdminMaintenanceFails.add();
                throw new RuntimeException(
                        String.format("Maintainer failed to execute command: %s, Exit code: %d, Stdout/stderr: %s",
                                Arrays.toString(command), result.getFirst(), result.getSecond()));
            }
            return result.getSecond().trim();
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute maintainer", e);
        }
    }

    /**
     * Wrapper for node-admin-maintenance, queues up maintenances jobs and sends a single request to maintenance JVM
     */
    private class MaintainerExecutor {
        private final List<MaintainerExecutorJob> jobs = new ArrayList<>();

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

            executeMaintainer("com.yahoo.vespa.hosted.node.maintainer.Maintainer", args);
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
        maintenanceThrottlerByContainerName.putIfAbsent(containerName, new MaintenanceThrottler());
        return maintenanceThrottlerByContainerName.get(containerName);
    }

    private class MaintenanceThrottler {
        private Instant nextRemoveOldFilesAt = Instant.EPOCH;
        private Instant nextHandleOldCoredumpsAt = Instant.EPOCH;

        void updateNextRemoveOldFilesTime() {
            nextRemoveOldFilesAt = clock.instant().plus(Duration.ofHours(1));
        }

        boolean shouldRemoveOldFilesNow() {
            return !nextRemoveOldFilesAt.isAfter(clock.instant());
        }

        void updateNextHandleCoredumpsTime() {
            nextHandleOldCoredumpsAt = clock.instant().plus(Duration.ofMinutes(5));
        }

        boolean shouldHandleCoredumpsNow() {
            return !nextHandleOldCoredumpsAt.isAfter(clock.instant());
        }

        void reset() {
            nextRemoveOldFilesAt = Instant.EPOCH;
            nextHandleOldCoredumpsAt = Instant.EPOCH;
        }
    }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.NodeType;
import com.yahoo.io.IOUtils;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerNetworking;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.logging.FilebeatConfigProvider;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileHelper;
import com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.node.admin.util.SecretAgentCheckConfig;
import com.yahoo.vespa.hosted.node.admin.maintenance.coredump.CoredumpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * @author freva
 */
public class StorageMaintainer {

    private final DockerOperations dockerOperations;
    private final ProcessExecuter processExecuter;
    private final Environment environment;
    private final CoredumpHandler coredumpHandler;
    private final FileHelper fileHelper;

    public StorageMaintainer(DockerOperations dockerOperations, ProcessExecuter processExecuter,
                             Environment environment, CoredumpHandler coredumpHandler, FileHelper fileHelper) {
        this.dockerOperations = dockerOperations;
        this.processExecuter = processExecuter;
        this.environment = environment;
        this.coredumpHandler = coredumpHandler;
        this.fileHelper = fileHelper;
    }

    public void writeMetricsConfig(ContainerName containerName, NodeSpec node) {
        List<SecretAgentCheckConfig> configs = new ArrayList<>();

        // host-life
        Path hostLifeCheckPath = environment.pathInNodeUnderVespaHome("libexec/yms/yms_check_host_life");
        SecretAgentCheckConfig hostLifeSchedule = new SecretAgentCheckConfig("host-life", 60, hostLifeCheckPath);
        configs.add(annotatedCheck(node, hostLifeSchedule));

        // ntp
        Path ntpCheckPath = environment.pathInNodeUnderVespaHome("libexec/yms/yms_check_ntp");
        SecretAgentCheckConfig ntpSchedule = new SecretAgentCheckConfig("ntp", 60, ntpCheckPath);
        configs.add(annotatedCheck(node, ntpSchedule));

        // coredumps (except for the done coredumps which is handled by the host)
        Path coredumpCheckPath = environment.pathInNodeUnderVespaHome("libexec/yms/yms_check_coredumps");
        SecretAgentCheckConfig coredumpSchedule = new SecretAgentCheckConfig("system-coredumps-processing", 300,
                coredumpCheckPath, "--application", "system-coredumps-processing", "--lastmin",
                "129600", "--crit", "1", "--coredir", environment.pathInNodeUnderVespaHome("var/crash/processing").toString());
        configs.add(annotatedCheck(node, coredumpSchedule));

        // athenz certificate check
        Path athenzCertExpiryCheckPath = environment.pathInNodeUnderVespaHome("libexec64/yms/yms_check_athenz_certs");
        SecretAgentCheckConfig athenzCertExpirySchedule = new SecretAgentCheckConfig("athenz-certificate-expiry", 60,
                 athenzCertExpiryCheckPath, "--threshold", "20")
                .withRunAsUser("root");
        configs.add(annotatedCheck(node, athenzCertExpirySchedule));

        if (node.getNodeType() != NodeType.config) {
            // vespa-health
            Path vespaHealthCheckPath = environment.pathInNodeUnderVespaHome("libexec/yms/yms_check_vespa_health");
            SecretAgentCheckConfig vespaHealthSchedule = new SecretAgentCheckConfig("vespa-health", 60, vespaHealthCheckPath, "all");
            configs.add(annotatedCheck(node, vespaHealthSchedule));

            // vespa
            Path vespaCheckPath = environment.pathInNodeUnderVespaHome("libexec/yms/yms_check_vespa");
            SecretAgentCheckConfig vespaSchedule = new SecretAgentCheckConfig("vespa", 60, vespaCheckPath, "all");
            configs.add(annotatedCheck(node, vespaSchedule));
        }

        if (node.getNodeType() == NodeType.config) {
            // configserver
            Path configServerCheckPath = environment.pathInNodeUnderVespaHome("libexec/yms/yms_check_ymonsb2");
            SecretAgentCheckConfig configServerSchedule = new SecretAgentCheckConfig("configserver", 60,
                    configServerCheckPath, "-zero", "configserver");
            configs.add(annotatedCheck(node, configServerSchedule));

            //zkbackupage
            Path zkbackupCheckPath = environment.pathInNodeUnderVespaHome("libexec/yamas2/yms_check_file_age.py");
            SecretAgentCheckConfig zkbackupSchedule = new SecretAgentCheckConfig("zkbackupage", 300,
                    zkbackupCheckPath, "-f", environment.pathInNodeUnderVespaHome("var/vespa-hosted/zkbackup.stat").toString(),
                    "-m", "150", "-a", "config-zkbackupage");
            configs.add(annotatedCheck(node, zkbackupSchedule));
        }

        if (node.getNodeType() == NodeType.proxy) {
            //routing-configage
            Path routingAgeCheckPath = environment.pathInNodeUnderVespaHome("libexec/yamas2/yms_check_file_age.py");
            SecretAgentCheckConfig routingAgeSchedule = new SecretAgentCheckConfig("routing-configage", 60,
                    routingAgeCheckPath, "-f", environment.pathInNodeUnderVespaHome("var/vespa-hosted/routing/nginx.conf").toString(),
                    "-m", "90", "-a", "routing-configage");
            configs.add(annotatedCheck(node, routingAgeSchedule));

            //ssl-check
            Path sslCheckPath = environment.pathInNodeUnderVespaHome("libexec/yms/yms_check_ssl_status");
            SecretAgentCheckConfig sslSchedule = new SecretAgentCheckConfig("ssl-status", 300,
                    sslCheckPath, "-e", "localhost", "-p", "4443", "-t", "30");
            configs.add(annotatedCheck(node, sslSchedule));
        }

        // Write config and restart yamas-agent
        Path yamasAgentFolder = environment.pathInNodeAdminFromPathInNode(containerName, Paths.get("/etc/yamas-agent/"));
        configs.forEach(s -> IOExceptionUtil.uncheck(() -> s.writeTo(yamasAgentFolder)));
        final String[] restartYamasAgent = new String[]{"service", "yamas-agent", "restart"};
        dockerOperations.executeCommandInContainerAsRoot(containerName, restartYamasAgent);
    }

    private SecretAgentCheckConfig annotatedCheck(NodeSpec node, SecretAgentCheckConfig check) {
        check.withTag("namespace", "Vespa")
                .withTag("role", SecretAgentCheckConfig.nodeTypeToRole(node.getNodeType()))
                .withTag("flavor", node.getFlavor())
                .withTag("canonicalFlavor", node.getCanonicalFlavor())
                .withTag("state", node.getState().toString())
                .withTag("zone", environment.getZone())
                .withTag("parentHostname", environment.getParentHostHostname());
        node.getOwner().ifPresent(owner -> check
                .withTag("tenantName", owner.getTenant())
                .withTag("app", owner.getApplication() + "." + owner.getInstance())
                .withTag("applicationName", owner.getApplication())
                .withTag("instanceName", owner.getInstance())
                .withTag("applicationId", owner.getTenant() + "." + owner.getApplication() + "." + owner.getInstance()));
        node.getMembership().ifPresent(membership -> check
                .withTag("clustertype", membership.getClusterType())
                .withTag("clusterid", membership.getClusterId()));
        node.getVespaVersion().ifPresent(version -> check.withTag("vespaVersion", version));

        return check;
    }

    public void writeFilebeatConfig(ContainerName containerName, NodeSpec node) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(StorageMaintainer.class, containerName);
        try {
            FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(environment);
            Optional<String> config = filebeatConfigProvider.getConfig(node);
            if (!config.isPresent()) return;

            Path filebeatPath = environment.pathInNodeAdminFromPathInNode(
                    containerName, Paths.get("/etc/filebeat/filebeat.yml"));
            Files.write(filebeatPath, config.get().getBytes());
            logger.info("Wrote filebeat config.");
        } catch (Throwable t) {
            logger.error("Failed writing filebeat config; " + node, t);
        }
    }

    public Optional<Long> getDiskUsageFor(ContainerName containerName) {
        Path containerDir = environment.pathInNodeAdminFromPathInNode(containerName, Paths.get("/home/"));
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
        if (!Files.exists(path)) {
            return 0;
        }

        final String[] command = {"du", "-xsk", path.toString()};

        Process duCommand = new ProcessBuilder().command(command).start();
        if (!duCommand.waitFor(60, TimeUnit.SECONDS)) {
            duCommand.destroy();
            duCommand.waitFor();
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
        Path[] logPaths = {
                environment.pathInNodeUnderVespaHome("logs/elasticsearch2"),
                environment.pathInNodeUnderVespaHome("logs/logstash2"),
                environment.pathInNodeUnderVespaHome("logs/daemontools_y"),
                environment.pathInNodeUnderVespaHome("logs/nginx"),
                environment.pathInNodeUnderVespaHome("logs/vespa")
        };

        for (Path pathToClean : logPaths) {
            Path path = environment.pathInNodeAdminFromPathInNode(containerName, pathToClean);
            fileHelper.streamFiles(path)
                    .filterFile(FileHelper.olderThan(Duration.ofDays(3))
                            .and(FileHelper.nameMatches(Pattern.compile(".*\\.log.+"))))
                    .delete();
        }

        Path qrsDir = environment.pathInNodeAdminFromPathInNode(
                containerName, environment.pathInNodeUnderVespaHome("logs/vespa/qrs"));
        fileHelper.streamFiles(qrsDir)
                .filterFile(FileHelper.olderThan(Duration.ofDays(3)))
                .delete();

        Path logArchiveDir = environment.pathInNodeAdminFromPathInNode(
                containerName, environment.pathInNodeUnderVespaHome("logs/vespa/logarchive"));
        fileHelper.streamFiles(logArchiveDir)
                .filterFile(FileHelper.olderThan(Duration.ofDays(31)))
                .delete();

        Path fileDistrDir = environment.pathInNodeAdminFromPathInNode(
                containerName, environment.pathInNodeUnderVespaHome("var/db/vespa/filedistribution"));
        fileHelper.streamFiles(fileDistrDir)
                .filterFile(FileHelper.olderThan(Duration.ofDays(31)))
                .recursive(true)
                .delete();
    }

    /**
     * Checks if container has any new coredumps, reports and archives them if so
     */
    public void handleCoreDumpsForContainer(ContainerName containerName, NodeSpec node) {
        final Path coredumpsPath = environment.pathInNodeAdminFromPathInNode(
                containerName, environment.pathInNodeUnderVespaHome("var/crash"));
        final Map<String, Object> nodeAttributes = getCoredumpNodeAttributes(node);
        coredumpHandler.processAll(coredumpsPath, nodeAttributes);
    }

    private Map<String, Object> getCoredumpNodeAttributes(NodeSpec node) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("hostname", node.getHostname());
        attributes.put("parent_hostname", environment.getParentHostHostname());
        attributes.put("region", environment.getRegion());
        attributes.put("environment", environment.getEnvironment());
        attributes.put("flavor", node.getFlavor());
        attributes.put("kernel_version", System.getProperty("os.version"));

        node.getCurrentDockerImage().ifPresent(image -> attributes.put("docker_image", image.asString()));
        node.getVespaVersion().ifPresent(version -> attributes.put("vespa_version", version));
        node.getOwner().ifPresent(owner -> {
            attributes.put("tenant", owner.getTenant());
            attributes.put("application", owner.getApplication());
            attributes.put("instance", owner.getInstance());
        });
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Prepares the container-storage for the next container by archiving container logs to a new directory
     * and deleting everything else owned by this container.
     */
    public void cleanupNodeStorage(ContainerName containerName, NodeSpec node) {
        removeOldFilesFromNode(containerName);

        Path logsDirInContainer = environment.pathInNodeUnderVespaHome("logs");
        Path containerLogsInArchiveDir = environment.pathInNodeAdminToNodeCleanup(containerName).resolve(logsDirInContainer);
        Path containerLogsPathOnHost = environment.pathInNodeAdminFromPathInNode(containerName, logsDirInContainer);

        fileHelper.createDirectories(containerLogsInArchiveDir.getParent());
        fileHelper.moveIfExists(containerLogsPathOnHost, containerLogsInArchiveDir);

        fileHelper.streamContents(environment.pathInHostFromPathInNode(containerName, Paths.get("/")))
                .includeBase(true)
                .delete();
    }

    /**
     * Runs node-maintainer's SpecVerifier and returns its output
     * @param node Node specification containing the excepted values we want to verify against
     * @return new combined hardware divergence
     * @throws RuntimeException if exit code != 0
     */
    public String getHardwareDivergence(NodeSpec node) {
        List<String> arguments = new ArrayList<>(Arrays.asList("specification",
                "--disk", Double.toString(node.getMinDiskAvailableGb()),
                "--memory", Double.toString(node.getMinMainMemoryAvailableGb()),
                "--cpu_cores", Double.toString(node.getMinCpuCores()),
                "--is_ssd", Boolean.toString(node.isFastDisk()),
                "--bandwidth", Double.toString(node.getBandwidth()),
                "--ips", String.join(",", node.getIpAddresses())));

        if (environment.getDockerNetworking() == DockerNetworking.HOST_NETWORK) {
            arguments.add("--skip-reverse-lookup");
        }

        node.getHardwareDivergence().ifPresent(hardwareDivergence -> {
            arguments.add("--divergence");
            arguments.add(hardwareDivergence);
        });

        return executeMaintainer("com.yahoo.vespa.hosted.node.verification.Main", arguments.toArray(new String[0]));
    }


    private String executeMaintainer(String mainClass, String... args) {
        String[] command = Stream.concat(
                Stream.of("sudo",
                        "VESPA_HOSTNAME=" + getDefaults().vespaHostname(),
                        "VESPA_HOME=" + getDefaults().vespaHome(),
                        getDefaults().underVespaHome("libexec/vespa/node-admin/maintenance.sh"),
                        mainClass),
                Stream.of(args))
                .toArray(String[]::new);

        try {
            Pair<Integer, String> result = processExecuter.exec(command);

            if (result.getFirst() != 0) {
                throw new RuntimeException(
                        String.format("Maintainer failed to execute command: %s, Exit code: %d, Stdout/stderr: %s",
                                Arrays.toString(command), result.getFirst(), result.getSecond()));
            }
            return result.getSecond().trim();
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute maintainer", e);
        }
    }
}

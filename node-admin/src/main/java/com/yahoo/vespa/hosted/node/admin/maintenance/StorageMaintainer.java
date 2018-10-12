// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.NodeType;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerNetworking;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.logging.FilebeatConfigProvider;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.util.SecretAgentCheckConfig;
import com.yahoo.vespa.hosted.node.admin.maintenance.coredump.CoredumpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameMatches;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.olderThan;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;

/**
 * @author freva
 */
public class StorageMaintainer {
    private static final Logger logger = Logger.getLogger(StorageMaintainer.class.getName());
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final DockerOperations dockerOperations;
    private final ProcessExecuter processExecuter;
    private final Environment environment;
    private final CoredumpHandler coredumpHandler;
    private final Path archiveContainerStoragePath;

    public StorageMaintainer(DockerOperations dockerOperations, ProcessExecuter processExecuter,
                             Environment environment, CoredumpHandler coredumpHandler, Path archiveContainerStoragePath) {
        this.dockerOperations = dockerOperations;
        this.processExecuter = processExecuter;
        this.environment = environment;
        this.coredumpHandler = coredumpHandler;
        this.archiveContainerStoragePath = archiveContainerStoragePath;
    }

    public void writeMetricsConfig(NodeAgentContext context, NodeSpec node) {
        List<SecretAgentCheckConfig> configs = new ArrayList<>();

        // host-life
        Path hostLifeCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_host_life");
        SecretAgentCheckConfig hostLifeSchedule = new SecretAgentCheckConfig("host-life", 60, hostLifeCheckPath);
        configs.add(annotatedCheck(node, hostLifeSchedule));

        // ntp
        Path ntpCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_ntp");
        SecretAgentCheckConfig ntpSchedule = new SecretAgentCheckConfig("ntp", 60, ntpCheckPath);
        configs.add(annotatedCheck(node, ntpSchedule));

        // coredumps (except for the done coredumps which is handled by the host)
        Path coredumpCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_coredumps");
        SecretAgentCheckConfig coredumpSchedule = new SecretAgentCheckConfig("system-coredumps-processing", 300,
                coredumpCheckPath, "--application", "system-coredumps-processing", "--lastmin",
                "129600", "--crit", "1", "--coredir", context.pathInNodeUnderVespaHome("var/crash/processing").toString());
        configs.add(annotatedCheck(node, coredumpSchedule));

        // athenz certificate check
        Path athenzCertExpiryCheckPath = context.pathInNodeUnderVespaHome("libexec64/yms/yms_check_athenz_certs");
        SecretAgentCheckConfig athenzCertExpirySchedule = new SecretAgentCheckConfig("athenz-certificate-expiry", 60,
                 athenzCertExpiryCheckPath, "--threshold", "20")
                .withRunAsUser("root");
        configs.add(annotatedCheck(node, athenzCertExpirySchedule));

        if (context.nodeType() != NodeType.config) {
            // vespa-health
            Path vespaHealthCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_vespa_health");
            SecretAgentCheckConfig vespaHealthSchedule = new SecretAgentCheckConfig("vespa-health", 60, vespaHealthCheckPath, "all");
            configs.add(annotatedCheck(node, vespaHealthSchedule));

            // vespa
            Path vespaCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_vespa");
            SecretAgentCheckConfig vespaSchedule = new SecretAgentCheckConfig("vespa", 60, vespaCheckPath, "all");
            configs.add(annotatedCheck(node, vespaSchedule));
        }

        if (context.nodeType() == NodeType.config) {
            // configserver
            Path configServerCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_ymonsb2");
            SecretAgentCheckConfig configServerSchedule = new SecretAgentCheckConfig("configserver", 60,
                    configServerCheckPath, "-zero", "configserver");
            configs.add(annotatedCheck(node, configServerSchedule));

            //zkbackupage
            Path zkbackupCheckPath = context.pathInNodeUnderVespaHome("libexec/yamas2/yms_check_file_age.py");
            SecretAgentCheckConfig zkbackupSchedule = new SecretAgentCheckConfig("zkbackupage", 300,
                    zkbackupCheckPath, "-f", context.pathInNodeUnderVespaHome("var/vespa-hosted/zkbackup.stat").toString(),
                    "-m", "150", "-a", "config-zkbackupage");
            configs.add(annotatedCheck(node, zkbackupSchedule));
        }

        if (context.nodeType() == NodeType.proxy) {
            //routing-configage
            Path routingAgeCheckPath = context.pathInNodeUnderVespaHome("libexec/yamas2/yms_check_file_age.py");
            SecretAgentCheckConfig routingAgeSchedule = new SecretAgentCheckConfig("routing-configage", 60,
                    routingAgeCheckPath, "-f", context.pathInNodeUnderVespaHome("var/vespa-hosted/routing/nginx.conf").toString(),
                    "-m", "90", "-a", "routing-configage");
            configs.add(annotatedCheck(node, routingAgeSchedule));

            //ssl-check
            Path sslCheckPath = context.pathInNodeUnderVespaHome("libexec/yms/yms_check_ssl_status");
            SecretAgentCheckConfig sslSchedule = new SecretAgentCheckConfig("ssl-status", 300,
                    sslCheckPath, "-e", "localhost", "-p", "4443", "-t", "30");
            configs.add(annotatedCheck(node, sslSchedule));
        }

        // Write config and restart yamas-agent
        Path yamasAgentFolder = context.pathOnHostFromPathInNode("/etc/yamas-agent");
        configs.forEach(s -> uncheck(() -> s.writeTo(yamasAgentFolder)));
        dockerOperations.executeCommandInContainerAsRoot(context.containerName(), "service", "yamas-agent", "restart");
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

    public void writeFilebeatConfig(NodeAgentContext context, NodeSpec node) {
        try {
            FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(environment);
            Optional<String> config = filebeatConfigProvider.getConfig(context, node);
            if (!config.isPresent()) return;

            Path filebeatPath = context.pathOnHostFromPathInNode("/etc/filebeat/filebeat.yml");
            Files.write(filebeatPath, config.get().getBytes());
            context.log(logger, "Wrote filebeat config");
        } catch (Throwable t) {
            context.log(logger, LogLevel.ERROR, "Failed writing filebeat config", t);
        }
    }

    public Optional<Long> getDiskUsageFor(NodeAgentContext context) {
        Path containerDir = context.pathOnHostFromPathInNode("/");
        try {
            return Optional.of(getDiskUsedInBytes(containerDir));
        } catch (Throwable e) {
            context.log(logger, LogLevel.WARNING, "Problems during disk usage calculations in " + containerDir.toAbsolutePath(), e);
            return Optional.empty();
        }
    }

    // Public for testing
    long getDiskUsedInBytes(Path path) throws IOException, InterruptedException {
        if (!Files.exists(path)) return 0;

        Process duCommand = new ProcessBuilder().command("du", "-xsk", path.toString()).start();
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


    /** Deletes old log files for vespa, nginx, logstash, etc. */
    public void removeOldFilesFromNode(NodeAgentContext context) {
        Path[] logPaths = {
                context.pathInNodeUnderVespaHome("logs/elasticsearch2"),
                context.pathInNodeUnderVespaHome("logs/logstash2"),
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
    }

    /** Checks if container has any new coredumps, reports and archives them if so */
    public void handleCoreDumpsForContainer(NodeAgentContext context, NodeSpec node) {
        final Map<String, Object> nodeAttributes = getCoredumpNodeAttributes(node);
        coredumpHandler.converge(context, nodeAttributes);
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

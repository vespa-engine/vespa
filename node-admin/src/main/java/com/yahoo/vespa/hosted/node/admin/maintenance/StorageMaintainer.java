// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.io.IOUtils;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.node.admin.restapi.SecretAgentHandler;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.node.maintenance.DeleteOldAppData;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author valerijf
 */
public class StorageMaintainer {
    private static final PrefixLogger NODE_ADMIN_LOGGER = PrefixLogger.getNodeAdminLogger(StorageMaintainer.class);
    private static final String[] baseArguments = {"sudo", "/home/y/libexec/vespa/node-admin/maintenance.sh"};
    private static final long intervalSec = 1000;

    private final Object monitor = new Object();

    private Map<ContainerName, MetricsCache> metricsCacheByContainerName = new ConcurrentHashMap<>();
    private Random random = new Random();

    // When connecting a docker container to a docker network, the interface name is set to eth[interface number],
    // for ipv4 only container, there will be only 1 interface: the ipv4 interface at eth0. For ipv6 container
    // there will be 2 interfaces: ipv4 for and ipv6. Because of the order they are connected in, ipv4 will also get
    // eth0 in that case aswell while ipv6 will be eth1.
    private static final String ipv4NetworkInterfaceName = "eth0";
    private static final String ipv6NetworkInterfaceName = "eth1";

    public void updateDockerUsage(String hostname, ContainerName containerName, Docker.ContainerStats stats) {
        updateMetricsCacheForContainerIfNeeded(containerName);

        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(StorageMaintainer.class, containerName);
        try {
            SecretAgentHandler secretAgentHandler = new SecretAgentHandler();
            secretAgentHandler.withDimension("host", hostname);

            getRelevantMetricsFromDockerStats(stats).forEach(secretAgentHandler::withMetric);
            metricsCacheByContainerName.get(containerName).metrics.forEach(secretAgentHandler::withMetric);

            // First write to temp file, then move temp file to main file to achieve atomic write
            Path metricsSharePath = Maintainer.pathInNodeAdminFromPathInNode(containerName, "/metrics-share/docker.stats");
            Path metricsSharePathTemp = Paths.get(metricsSharePath.toString() + "_temp");
            Files.write(metricsSharePathTemp, secretAgentHandler.toJson().getBytes(StandardCharsets.UTF_8.name()));

            // Files.move() fails to move if target already exist, could do target.delete() first, but then it's no longer atomic
            execute(logger, "mv", metricsSharePathTemp.toString(), metricsSharePath.toString());
        } catch (IOException e) {
            logger.warning("Failed to get/write docker container stats", e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getRelevantMetricsFromDockerStats(Docker.ContainerStats stats) {
        Map<String, Object> relevantStats = new HashMap<>();

        Map<String, Object> throttledData = (Map<String, Object>) stats.getCpuStats().get("throttling_data");
        Map<String, Object> cpuUsage = (Map<String, Object>) stats.getCpuStats().get("cpu_usage");
        relevantStats.put("node.cpu.throttled_time", throttledData.get("throttled_time"));
        relevantStats.put("node.cpu.system_cpu_usage", stats.getCpuStats().get("system_cpu_usage"));
        relevantStats.put("node.cpu.total_usage", cpuUsage.get("total_usage"));

        relevantStats.put("node.memory.limit", stats.getMemoryStats().get("limit"));
        relevantStats.put("node.memory.usage", stats.getMemoryStats().get("usage"));

        Map<String, Object> ipv4stats = (Map<String, Object>) stats.getNetworks().get(ipv4NetworkInterfaceName);
        relevantStats.put("node.network.ipv4.bytes_rcvd", ipv4stats.get("rx_bytes"));
        relevantStats.put("node.network.ipv4.bytes_sent", ipv4stats.get("tx_bytes"));

        if (stats.getNetworks().size() == 2) {
            Map<String, Object> ipv6stats = (Map<String, Object>) stats.getNetworks().get(ipv6NetworkInterfaceName);
            relevantStats.put("node.network.ipv6.bytes_rcvd", ipv6stats.get("rx_bytes"));
            relevantStats.put("node.network.ipv6.bytes_sent", ipv6stats.get("tx_bytes"));
        }

        return relevantStats;
    }

    private void updateMetricsCacheForContainerIfNeeded(ContainerName containerName) {
        // Calculating disk usage is IO expensive operation and its value changes relatively slowly, we want to perform
        // that calculation rarely. Additionally, we spread out the calculation for different containers by adding
        // a random deviation.
        if (metricsCacheByContainerName.containsKey(containerName) &&
                metricsCacheByContainerName.get(containerName).nextUpdateAt.isAfter(Instant.now())) return;

        long distributedSecs = (long) (intervalSec * (0.5 + random.nextDouble()));
        MetricsCache metricsCache = new MetricsCache(Instant.now().plusSeconds(distributedSecs));

        // Throttle to one disk usage calculation at a time.
        synchronized (monitor) {
            PrefixLogger logger = PrefixLogger.getNodeAgentLogger(StorageMaintainer.class, containerName);
            File containerDir = Maintainer.pathInNodeAdminFromPathInNode(containerName, "/home/").toFile();
            try {
                long used = getDiscUsedInBytes(containerDir);
                metricsCache.metrics.put("node.disk.used", used);
            } catch (Throwable e) {
                logger.error("Problems during disk usage calculations: " + e.getMessage());
            }
        }

        metricsCacheByContainerName.put(containerName, metricsCache);
    }

    // Public for testing
    long getDiscUsedInBytes(File path) throws IOException, InterruptedException {
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


    public void removeOldFilesFromNode(ContainerName containerName) {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(StorageMaintainer.class, containerName);

        String[] pathsToClean = {"/home/y/logs/elasticsearch2", "/home/y/logs/logstash2",
                "/home/y/logs/daemontools_y", "/home/y/logs/nginx", "/home/y/logs/vespa"};
        for (String pathToClean : pathsToClean) {
            File path = Maintainer.pathInNodeAdminFromPathInNode(containerName, pathToClean).toFile();
            if (path.exists()) {
                DeleteOldAppData.deleteFiles(path.getAbsolutePath(), Duration.ofDays(3).getSeconds(), ".*\\.log\\..+", false);
                DeleteOldAppData.deleteFiles(path.getAbsolutePath(), Duration.ofDays(3).getSeconds(), ".*QueryAccessLog.*", false);
            }
        }

        File logArchiveDir = Maintainer.pathInNodeAdminFromPathInNode(containerName, "/home/y/logs/vespa/logarchive").toFile();
        if (logArchiveDir.exists()) {
            DeleteOldAppData.deleteFiles(logArchiveDir.getAbsolutePath(), Duration.ofDays(31).getSeconds(), null, false);
        }

        File fileDistrDir = Maintainer.pathInNodeAdminFromPathInNode(containerName, "/home/y/var/db/vespa/filedistribution").toFile();
        if (fileDistrDir.exists()) {
            DeleteOldAppData.deleteFiles(fileDistrDir.getAbsolutePath(), Duration.ofDays(31).getSeconds(), null, false);
        }

        execute(logger, concatenateArrays(baseArguments, Maintainer.JOB_CLEAN_CORE_DUMPS));
    }

    public void cleanNodeAdmin() {
        execute(NODE_ADMIN_LOGGER, concatenateArrays(baseArguments, Maintainer.JOB_DELETE_OLD_APP_DATA));
        execute(NODE_ADMIN_LOGGER, concatenateArrays(baseArguments, Maintainer.JOB_CLEAN_HOME));

        File nodeAdminJDiskLogsPath = Maintainer.pathInNodeAdminFromPathInNode(new ContainerName("node-admin"),
                "/home/y/logs/jdisc_core/").toFile();
        DeleteOldAppData.deleteFiles(nodeAdminJDiskLogsPath.getAbsolutePath(), Duration.ofDays(31).getSeconds(), null, false);
    }

    public void deleteContainerStorage(ContainerName containerName) throws IOException {
        PrefixLogger logger = PrefixLogger.getNodeAgentLogger(StorageMaintainer.class, containerName);

        File yVarDir = Maintainer.pathInNodeAdminFromPathInNode(containerName, "/home/y/var").toFile();
        if (yVarDir.exists()) {
            DeleteOldAppData.deleteDirectories(yVarDir.getAbsolutePath(), 0, null);
        }

        Path from = Maintainer.pathInNodeAdminFromPathInNode(containerName, "/");
        if (!Files.exists(from)) {
            logger.info("The application storage at " + from + " doesn't exist");
            return;
        }

        Path to = Maintainer.pathInNodeAdminToNodeCleanup(containerName);
        logger.info("Deleting application storage by moving it from " + from + " to " + to);
        //TODO: move to maintenance JVM
        Files.move(from, to);
    }

    private void execute(PrefixLogger logger, String... params) {
        try {
            Process p = Runtime.getRuntime().exec(params);
            String output = IOUtils.readAll(new InputStreamReader(p.getInputStream()));
            String errors = IOUtils.readAll(new InputStreamReader(p.getErrorStream()));

            if (! output.isEmpty()) logger.info(output);
            if (! errors.isEmpty()) logger.error(errors);
        } catch (IOException e) {
            NODE_ADMIN_LOGGER.warning("Failed to execute command " + Arrays.toString(params), e);
        }
    }

    private static String[] concatenateArrays(String[] ar1, String... ar2) {
        String[] concatenated = new String[ar1.length + ar2.length];
        System.arraycopy(ar1, 0, concatenated, 0, ar1.length);
        System.arraycopy(ar2, 0, concatenated, ar1.length, ar2.length);
        return concatenated;
    }

    private static class MetricsCache {
        private final Instant nextUpdateAt;
        private final Map<String, Object> metrics = new HashMap<>();

        MetricsCache(Instant nextUpdateAt) {
            this.nextUpdateAt = nextUpdateAt;
        }
    }
}
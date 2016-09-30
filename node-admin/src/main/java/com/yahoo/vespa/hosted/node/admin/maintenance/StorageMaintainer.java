// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
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
import java.time.Duration;
import java.time.Instant;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Object monitor = new Object();
    private final long intervalSec = 1000;

    private Map<ContainerName, Instant> nextDiskCalculationByContainerName = new ConcurrentHashMap<>();
    private Random random = new Random();

    public void updateDiskUsage(String hostname, ContainerName containerName) {
        if (nextDiskCalculationByContainerName.containsKey(containerName)) {
            if (nextDiskCalculationByContainerName.get(containerName).isAfter(Instant.now())) {
                return;
            }
        }
        long distributedSecs = (long) (intervalSec * (0.5 + random.nextDouble()));
        nextDiskCalculationByContainerName.put(containerName, Instant.now().plusSeconds(distributedSecs));
        // Throttle to one disk usage calculation at a time.
        synchronized (monitor) {
            PrefixLogger logger = PrefixLogger.getNodeAgentLogger(StorageMaintainer.class, containerName);
            File containerDir = Maintainer.pathInNodeAdminFromPathInNode(containerName, "/home/").toFile();
            try {
                long used = getDiscUsedInBytes(containerDir);

                Map<String, Object> dimensions = new HashMap<>();
                dimensions.put("host", hostname);

                Map<String, Object> metrics = new HashMap<>();
                metrics.put("node.disk.used", used);

                Map<String, Object> secretAgentReport = SecretAgentHandler.generateSecretAgentReport(dimensions, metrics);
                String secretAgentReportJson = objectMapper.writeValueAsString(secretAgentReport);

                Path metricsSharePath = Maintainer.pathInNodeAdminFromPathInNode(containerName, "/metrics-share/disk.usage");
                Files.write(metricsSharePath, secretAgentReportJson.getBytes(StandardCharsets.UTF_8.name()));
            } catch (Throwable e) {
                logger.error("Problems during disk usage calculations: " + e.getMessage());
            }
        }
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

        execute(logger, Maintainer.JOB_CLEAN_CORE_DUMPS);
    }

    public void cleanNodeAdmin() {
        execute(NODE_ADMIN_LOGGER, Maintainer.JOB_DELETE_OLD_APP_DATA);
        execute(NODE_ADMIN_LOGGER, Maintainer.JOB_CLEAN_HOME);

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
            Process p = Runtime.getRuntime().exec(concatenateArrays(baseArguments, params));
            String output = IOUtils.readAll(new InputStreamReader(p.getInputStream()));
            String errors = IOUtils.readAll(new InputStreamReader(p.getErrorStream()));

            if (! output.isEmpty()) logger.info(output);
            if (! errors.isEmpty()) logger.error(errors);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String[] concatenateArrays(String[] ar1, String[] ar2) {
        String[] concatenated = new String[ar1.length + ar2.length];
        System.arraycopy(ar1, 0, concatenated, 0, ar1.length);
        System.arraycopy(ar2, 0, concatenated, ar1.length, ar2.length);
        return concatenated;
    }
}
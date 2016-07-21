package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import com.yahoo.vespa.hosted.node.maintenance.DeleteOldAppData;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Level;

/**
 * @author valerijf
 */
public class MaintenanceSchedulerImpl implements MaintenanceScheduler {
    private static final String[] baseArguments = {"sudo", "/home/y/libexec/vespa/node-admin/maintenance.sh"};

    @Override
    public void removeOldFilesFromNode(ContainerName containerName) {
        PrefixLogger logger = new PrefixLogger(MaintenanceSchedulerImpl.class.getName(), "NodeAgent-" + containerName.asString());

        String[] pathsToClean = {"/home/y/logs/elasticsearch2", "/home/y/logs/logstash2",
                "/home/y/logs/daemontools_y", "/home/y/logs/nginx", "/home/y/logs/vespa"};
        for (String pathToClean : pathsToClean) {
            File path = resolveContainerPath(containerName, pathToClean);
            if (path.exists()) {
                DeleteOldAppData.deleteFiles(path.getAbsolutePath(), Duration.ofDays(3).getSeconds(), ".*\\.log\\..+", false);
                DeleteOldAppData.deleteFiles(path.getAbsolutePath(), Duration.ofDays(3).getSeconds(), ".*QueryAccessLog.*", false);
            }
        }

        File logArchiveDir = resolveContainerPath(containerName, "/home/y/logs/vespa/logarchive");
        if (logArchiveDir.exists()) {
            DeleteOldAppData.deleteFiles(logArchiveDir.getAbsolutePath(), Duration.ofDays(31).getSeconds(), null, false);
        }

        File fileDistrDir = resolveContainerPath(containerName, "/home/y/var/db/vespa/filedistribution");
        if (fileDistrDir.exists()) {
            DeleteOldAppData.deleteFiles(fileDistrDir.getAbsolutePath(), Duration.ofDays(31).getSeconds(), null, false);
        }

        execute(logger, Maintainer.JOB_CLEAN_CORE_DUMPS);
    }

    @Override
    public void cleanNodeAdmin() {
        PrefixLogger logger = new PrefixLogger(MaintenanceSchedulerImpl.class.getName(), "NodeAdmin");
        execute(logger, Maintainer.JOB_DELETE_OLD_APP_DATA);
        execute(logger, Maintainer.JOB_CLEAN_HOME);
    }

    @Override
    public void deleteContainerStorage(ContainerName containerName) throws IOException {
        PrefixLogger logger = new PrefixLogger(MaintenanceSchedulerImpl.class.getName(), "NodeAgent-" + containerName.asString());

        File yVarDir = resolveContainerPath(containerName, "/home/y/var");
        if (yVarDir.exists()) {
            DeleteOldAppData.deleteDirectories(yVarDir.getAbsolutePath(), 0, null);
        }

        Path from = Maintainer.applicationStoragePathForNode(containerName);
        if (!Files.exists(from)) {
            logger.log(LogLevel.INFO, "The application storage at " + from + " doesn't exist");
            return;
        }

        Path to = Maintainer.applicationStoragePathForNodeCleanup(containerName);
        logger.log(LogLevel.INFO, "Deleting application storage by moving it from " + from + " to " + to);
        //TODO: move to maintenance JVM
        Files.move(from, to);
    }

    private void execute(PrefixLogger logger, String... params) {
        try {
            Process p = Runtime.getRuntime().exec(concatenateArrays(baseArguments, params));
            String output = IOUtils.readAll(new InputStreamReader(p.getInputStream()));
            String errors = IOUtils.readAll(new InputStreamReader(p.getErrorStream()));

            if (! output.isEmpty()) logger.log(Level.INFO, output);
            if (! errors.isEmpty()) logger.log(Level.SEVERE, errors);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File resolveContainerPath(ContainerName containerName, String relativePath) {
        return Maintainer.applicationStoragePathForNode(containerName).resolve(relativePath).toFile();
    }

    private static String[] concatenateArrays(String[] ar1, String[] ar2) {
        String[] concatenated = new String[ar1.length + ar2.length];
        System.arraycopy(ar1, 0, concatenated, 0, ar1.length);
        System.arraycopy(ar2, 0, concatenated, ar1.length, ar2.length);
        return concatenated;
    }
}
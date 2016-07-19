package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImpl;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author valerijf
 */
public class MaintenanceSchedulerImpl extends AbstractComponent implements Runnable, MaintenanceScheduler {
    protected final Logger log = Logger.getLogger(MaintenanceSchedulerImpl.class.getName());

    private final Duration rate = Duration.ofMinutes(1);
    private final ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1);
    private Queue<String> jobQueue = new LinkedBlockingQueue<>();

    private static MaintenanceScheduler maintenanceScheduler = null;

    // Because of the regular job queue, we must have only one instance of this class
    public static MaintenanceScheduler getInstance() {
        if (maintenanceScheduler == null) maintenanceScheduler = new MaintenanceSchedulerImpl();

        return maintenanceScheduler;
    }

    private MaintenanceSchedulerImpl() {
        service.scheduleAtFixedRate(this, rate.toMillis(), rate.toMillis(), TimeUnit.MILLISECONDS);

        addRegularJob(Maintainer.JOB_DELETE_OLD_APP_DATA,
                "--path=/host/home/docker/container-storage",
                "--max_age=" + Duration.ofDays(7).getSeconds(),
                "--name=^" + DockerImpl.APPLICATION_STORAGE_CLEANUP_PATH_PREFIX);
    }

    @Override
    public void addRegularJob(String... args) {
        jobQueue.add(String.join(" ", args));
    }

    @Override
    public void runMaintenanceJob(String... args) {
        execute(String.join(" ", args));
    }


    /**
     * Deletes directories and their contents if they match all the criteria
     *
     * @param basePath      Base path to delete the directories from
     * @param maxAgeSeconds Delete directories older (last modified date) than maxAgeSeconds
     */
    @Override
    public void deleteOldAppData(String basePath, long maxAgeSeconds) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_APP_DATA, "--path=" + basePath, "--max_age=" + maxAgeSeconds);
    }

    /**
     * Deletes directories and their contents if they match all the criteria
     *
     * @param basePath      Base path to delete the directories from
     * @param maxAgeSeconds Delete directories older (last modified date) than maxAgeSeconds
     * @param dirNameRegex  Delete directories where directory name matches dirNameRegex
     */
    @Override
    public void deleteOldAppData(String basePath, long maxAgeSeconds, String dirNameRegex) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_APP_DATA, "--path=" + basePath, "--max_age=" + maxAgeSeconds, "--name=" + dirNameRegex);
    }

    /**
     * Recursively deletes files if they match all the criteria, also deletes empty directories.

     * @param basePath      Base path from where to start the search
     * @param maxAgeSeconds Delete files older (last modified date) than maxAgeSeconds
     */
    @Override
    public void deleteOldLogs(String basePath, long maxAgeSeconds) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_LOGS, "--path=" + basePath, "--max_age=" + maxAgeSeconds);
    }

    /**
     * Recursively deletes files if they match all the criteria, also deletes empty directories.
     *
     * @param basePath      Base path from where to start the search
     * @param maxAgeSeconds Delete files older (last modified date) than maxAgeSeconds
     * @param fileNameRegex Delete files where filename matches fileNameRegex
     */
    @Override
    public void deleteOldLogs(String basePath, long maxAgeSeconds, String fileNameRegex) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_LOGS, "--path=" + basePath, "--max_age=" + maxAgeSeconds, "--name=" + fileNameRegex);
    }

    @Override
    public void cleanNodeAgent() {
        runMaintenanceJob(Maintainer.JOB_CLEAN_LOGS);
        runMaintenanceJob(Maintainer.JOB_CLEAN_LOGARCHIVE);
        runMaintenanceJob(Maintainer.JOB_CLEAN_FILEDISTRIBUTION);
        runMaintenanceJob(Maintainer.JOB_CLEAN_CORE_DUMPS);
    }

    @Override
    public void cleanNodeAdmin() {
        runMaintenanceJob(Maintainer.JOB_CLEAN_HOME);
    }

    @Override
    public void deleteContainerStorage(ContainerName containerName) throws IOException {
        deleteOldAppData("/home/y/var", 0);

        Path from = DockerImpl.applicationStoragePathForNodeAdmin(containerName.asString());
        if (!Files.exists(from)) {
            log.log(LogLevel.INFO, "The application storage at " + from + " doesn't exist");
            return;
        }

        Path to = DockerImpl.applicationStoragePathForNodeAdmin(DockerImpl.APPLICATION_STORAGE_CLEANUP_PATH_PREFIX +
                containerName.asString() + "_" + DockerImpl.filenameFormatter.format(Date.from(Instant.now())));
        log.log(LogLevel.INFO, "Deleting application storage by moving it from " + from + " to " + to);
        //TODO: move to maintenance JVM
        Files.move(from, to);
    }


    @Override
    public void run() {
        try {
            for (String args : jobQueue) {
                log.log(Level.INFO, "Maintenance: Running " + args);
                execute(args);
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, this + " failed. Will retry in " + rate.toMinutes() + " minutes", e);
        }
    }

    private void execute(String args) {
        try {
            Process p = Runtime.getRuntime().exec(
                    "sudo /home/y/libexec/vespa/node-admin/maintenance.sh " + args);
            String output = IOUtils.readAll(new InputStreamReader(p.getInputStream()));
            String errors = IOUtils.readAll(new InputStreamReader(p.getErrorStream()));

            if (! output.isEmpty()) log.log(Level.INFO, output);
            if (! errors.isEmpty()) log.log(Level.SEVERE, errors);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deconstruct() {
        service.shutdown();
    }
}
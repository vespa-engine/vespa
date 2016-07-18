package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImpl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
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
public class MaintenanceScheduler extends AbstractComponent implements Runnable {
    protected static final Logger log = Logger.getLogger(MaintenanceScheduler.class.getName());

    private static final Duration rate = Duration.ofMinutes(1);
    private static final ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1);
    private static Queue<String> jobQueue = new LinkedBlockingQueue<>();

    public MaintenanceScheduler() {
        service.scheduleAtFixedRate(this, rate.toMillis(), rate.toMillis(), TimeUnit.MILLISECONDS);

        addRegularJob(Maintainer.JOB_DELETE_OLD_APP_DATA,
                "--path=/host/home/docker/container-storage",
                "--max_age=" + Duration.ofDays(7).getSeconds(),
                "--name=^" + DockerImpl.APPLICATION_STORAGE_CLEANUP_PATH_PREFIX);
    }

    /**
     * Adds a maintenance job to regular queue. These jobs are run once every {@link MaintenanceScheduler#rate}.
     * @param args Job name and other optional additional arguments for the maintenance script
     */
    public static void addRegularJob(String... args) {
        jobQueue.add(String.join(" ", args));
    }

    /**
     * Executes a single maintenance job. This call is blocking.
     * @param args Job name and other optional additional arguments for the maintenance script
     */
    public static void runMaintenanceJob(String... args) {
        execute(String.join(" ", args));
    }


    public static void deleteOldAppData(String path, long maxAge) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_APP_DATA, "--path=" + path, "--max_age=" + maxAge);
    }

    public static void deleteOldAppData(String path, long maxAge, String name) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_APP_DATA, "--path=" + path, "--max_age=" + maxAge, "--name=" + name);
    }

    public static void deleteOldLogs(String path, long maxAge) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_LOGS, "--path=" + path, "--max_age=" + maxAge);
    }

    public static void deleteOldLogs(String path, long maxAge, String name) {
        runMaintenanceJob(Maintainer.JOB_DELETE_OLD_LOGS, "--path=" + path, "--max_age=" + maxAge, "--name=" + name);
    }

    public static void cleanNodeAgent() {
        runMaintenanceJob(Maintainer.JOB_CLEAN_LOGS);
        runMaintenanceJob(Maintainer.JOB_CLEAN_LOGARCHIVE);
        runMaintenanceJob(Maintainer.JOB_CLEAN_FILEDISTRIBUTION);
        runMaintenanceJob(Maintainer.JOB_CLEAN_CORE_DUMPS);
    }

    public static void cleanNodeAdmin() {
        runMaintenanceJob(Maintainer.JOB_CLEAN_HOME);
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

    private static void execute(String args) {
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
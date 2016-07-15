package com.yahoo.vespa.hosted.node.admin.maintenance;

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
public class MaintenanceScheduler implements Runnable {
    protected static final Logger log = Logger.getLogger(MaintenanceScheduler.class.getName());

    private static final Duration rate = Duration.ofMinutes(1);
    private static final ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1);
    private static Queue<String> jobQueue = new LinkedBlockingQueue<>();

    static {
        service.scheduleAtFixedRate(new MaintenanceScheduler(), rate.toMillis(), rate.toMillis(), TimeUnit.MILLISECONDS);
        addJob("delete-old-app-data",
                "--path=/host/home/docker/container-storage",
                "--max_age=" + Duration.ofDays(7).getSeconds(),
                "--name=^" + DockerImpl.APPLICATION_STORAGE_CLEANUP_PATH_PREFIX);
    }

    public static void addJob(String... args) {
        jobQueue.add(String.join(" ", args));
    }


    @Override
    public void run() {
        try {
            for (String args : jobQueue) {
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
        } catch (RuntimeException e) {
            log.log(Level.WARNING, this + " failed. Will retry in " + rate.toMinutes() + " minutes", e);
        }
    }

    public static void deconstruct() {
        service.shutdown();
    }
}

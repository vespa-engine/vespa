package com.yahoo.vespa.hosted.node.admin.maintenance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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

    private final Duration rate;
    private final ScheduledExecutorService service;
    private static Queue<MaintenanceJob> jobQueue = new LinkedBlockingQueue<>();

    public MaintenanceScheduler(Duration rate) {
        this.rate = rate;

        this.service = new ScheduledThreadPoolExecutor(1);
        this.service.scheduleAtFixedRate(this, rate.toMillis(), rate.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static void addJob(MaintenanceJob job) {
        jobQueue.add(job);
    }


    @Override
    public void run() {
        try {
            while(jobQueue.size() > 0) {
                try {
                    MaintenanceJob job = jobQueue.remove();
                    Process p = Runtime.getRuntime().exec("scripts/maintenance.sh " + String.join(" ", job.getArgs()));
                    String output = inputStreamToString(p.getInputStream());
                    String errors = inputStreamToString(p.getErrorStream());

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

    private static String inputStreamToString(InputStream outputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(outputStream));

        String line;
        StringBuilder stringBuilder = new StringBuilder();
        while((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line).append("\r\n");
        }
        return stringBuilder.toString();
    }

    public void deconstruct() {
        this.service.shutdown();
    }


    public static class MaintenanceJob {
        private String[] args;

        public MaintenanceJob(String[] args) {
            this.args = args;
        }

        public String[] getArgs() {
            return args;
        }
    }
}

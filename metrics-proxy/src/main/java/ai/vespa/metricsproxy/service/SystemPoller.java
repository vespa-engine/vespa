// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import java.util.logging.Level;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Class to get data from the system and update the services at given intervals.
 * TODO: rewrite to use ScheduledExecutorService or just call poll() directly.
 *
 * @author Eirik Nygaard
 */
public class SystemPoller {

    private static final Logger log = Logger.getLogger(SystemPoller.class.getName());

    private final int pollingIntervalSecs;
    private final List<VespaService> services;

    private final int memoryTypeVirtual = 0;
    private final int memoryTypeResident = 1;
    private final Map<VespaService, Long> lastCpuJiffiesMetrics = new ConcurrentHashMap<>();
    private final Timer systemPollTimer;

    private long lastTotalCpuJiffies = -1;

    public SystemPoller(List<VespaService> services, int pollingIntervalSecs) {
        this.services = services;
        this.pollingIntervalSecs = pollingIntervalSecs;
        systemPollTimer = new Timer("systemPollTimer", true);
    }

    void stop() {
        systemPollTimer.cancel();
    }

    /**
     * Return memory usage for a given process, both resident and virtual is
     * returned.
     *
     * @param service The instance to get memory usage for
     * @return array[0] = memoryResident, array[1] = memoryVirtual (kB units)
     */
    long[] getMemoryUsage(VespaService service) {
        long[] size = new long[2];
        BufferedReader br;
        int pid = service.getPid();

        try {
            br = new BufferedReader(new FileReader("/proc/" + pid + "/smaps"));
        } catch (FileNotFoundException ex) {
            service.setAlive(false);
            return size;
        }
        String line;
        try {
            while ((line = br.readLine()) != null) {
                String[] elems = line.split("\\s+");
                /* Memory size is given in kB - convert to bytes by multiply with 1024*/
                if (line.startsWith("Rss:")) {
                    size[memoryTypeResident] += Long.parseLong(elems[1]) * 1024;
                } else if (line.startsWith("Size:")) {
                    size[memoryTypeVirtual] += Long.parseLong(elems[1]) * 1024;
                }
            }

            br.close();
        } catch (IOException ex) {
            log.log(Level.FINE, "Unable to read line from smaps file", ex);
            return size;
        }

        return size;
    }

    /**
     * Poll services for system metrics
     */
    void poll() {
        long startTime = System.currentTimeMillis();
        boolean someAlive = false;

        /* Don't do any work if there are no known services */
        if (services.isEmpty()) {
            schedule();
            return;
        }

        log.log(Level.FINE, "Monitoring system metrics for " + services.size() + " services");

        long sysJiffies = getNormalizedSystemJiffies();
        for (VespaService s : services) {


            if(s.isAlive()) {
                someAlive = true;
            }

            Metrics metrics = new Metrics();
            log.log(Level.FINE, "Current size of system metrics for service  " + s + " is " + metrics.size());

            long[] size = getMemoryUsage(s);
            log.log(Level.FINE, "Updating memory metric for service " + s);

            metrics.add(new Metric("memory_virt", size[memoryTypeVirtual], startTime / 1000));
            metrics.add(new Metric("memory_rss", size[memoryTypeResident], startTime / 1000));

            long procJiffies = getPidJiffies(s);
            if (lastTotalCpuJiffies >= 0 && lastCpuJiffiesMetrics.containsKey(s)) {
                long last = lastCpuJiffiesMetrics.get(s);
                long diff = procJiffies - last;

                if (diff >= 0) {
                    metrics.add(new Metric("cpu", 100 * ((double) diff) / (sysJiffies - lastTotalCpuJiffies), startTime / 1000));
                }
            }
            lastCpuJiffiesMetrics.put(s, procJiffies);
            s.setSystemMetrics(metrics);
        }

        lastTotalCpuJiffies = sysJiffies;

        // If none of the services were alive, reschedule in a short time
        if (!someAlive) {
            reschedule(System.currentTimeMillis() - startTime);
        } else {
            schedule();
        }
    }

    long getPidJiffies(VespaService service) {
        BufferedReader in;
        String line;
        String[] elems;
        int pid = service.getPid();

        try {
            in = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
        } catch (FileNotFoundException ex) {
            log.log(Level.FINE, "Unable to find pid " + pid + " in proc directory, for service " + service.getInstanceName());
            service.setAlive(false);
            return 0;
        }

        try {
            line = in.readLine();
            in.close();
        } catch (IOException ex) {
            log.log(Level.FINE, "Unable to read line from process stat file", ex);
            return 0;
        }

        elems = line.split(" ");

        /* Add user mode and kernel mode jiffies for the given process */
        return Long.parseLong(elems[13]) + Long.parseLong(elems[14]);
    }

    long getNormalizedSystemJiffies() {
        BufferedReader in;
        String line;
        ArrayList<CpuJiffies> jiffies = new ArrayList<>();
        CpuJiffies total = null;

        try {
            in = new BufferedReader(new FileReader("/proc/stat"));
        } catch (FileNotFoundException ex) {
            log.log(Level.SEVERE, "Unable to open stat file", ex);
            return 0;
        }
        try {
            while ((line = in.readLine()) != null) {
                if (line.startsWith("cpu ")) {
                    total = new CpuJiffies(line);
                } else if (line.startsWith("cpu")) {
                    jiffies.add(new CpuJiffies(line));
                }
            }

            in.close();
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Unable to read line from stat file", ex);
            return 0;
        }

        /* Normalize so that a process that uses an entire CPU core will get 100% util */
        if (total != null) {
            return total.getTotalJiffies() / jiffies.size();
        } else {
            return 0;
        }
    }

    private void schedule(long time) {
        try {
            systemPollTimer.schedule(new PollTask(this), time);
        } catch(IllegalStateException e){
            log.info("Tried to schedule task, but timer was already shut down.");
        }
    }

    public void schedule() {
        schedule(pollingIntervalSecs * 1000);
    }

    private void reschedule(long skew) {
        long sleep = (pollingIntervalSecs * 1000) - skew;

        // Don't sleep less than 1 min
        sleep = Math.max(60 * 1000, sleep);
        schedule(sleep);
    }


    private static class PollTask extends TimerTask {
        private final SystemPoller poller;

        PollTask(SystemPoller poller) {
            this.poller = poller;
        }

        @Override
        public void run() {
            poller.poll();
        }
    }
}

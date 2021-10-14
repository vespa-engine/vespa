// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.model.MetricId;

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
    private static final int memoryTypeVirtual = 0;
    private static final int memoryTypeResident = 1;
    private static final MetricId CPU = MetricId.toMetricId("cpu");
    private static final MetricId CPU_UTIL = MetricId.toMetricId("cpu_util");
    private static final MetricId MEMORY_VIRT = MetricId.toMetricId("memory_virt");
    private static final MetricId MEMORY_RSS = MetricId.toMetricId("memory_rss");

    private final int pollingIntervalSecs;
    private final List<VespaService> services;
    private final Map<VespaService, Long> lastCpuJiffiesMetrics = new ConcurrentHashMap<>();
    private final Timer systemPollTimer;
    private final GetJiffies jiffiesInterface;

    private JiffiesAndCpus lastTotalCpuJiffies;

    static class JiffiesAndCpus {
        final long jiffies;
        final int cpus;
        JiffiesAndCpus() { this(0,1); }
        JiffiesAndCpus(long jiffies, int cpus) {
            this.jiffies = jiffies;
            this.cpus = Math.max(1, cpus);
        }
        /** 1.0 = 1 busy core Range = [0.0, #cores] */
        double ratioSingleCoreJiffies(long partJiffies) {
            return (double)(partJiffies * cpus) / Math.max(1.0, jiffies);
        }
        /** Range = [0.0, 1.0] */
        double ratioJiffies(long partJiffies) {
            return (double)(partJiffies) / Math.max(1.0, jiffies);
        }
        JiffiesAndCpus diff(JiffiesAndCpus prev) {
            return (cpus == prev.cpus)
                    ? new JiffiesAndCpus(jiffies - prev.jiffies, cpus)
                    : new JiffiesAndCpus();
        }
    }
    interface GetJiffies {
        JiffiesAndCpus getTotalSystemJiffies();
        long getJiffies(VespaService service);
    }

    public SystemPoller(List<VespaService> services, int pollingIntervalSecs) {
        this.services = services;
        this.pollingIntervalSecs = pollingIntervalSecs;
        systemPollTimer = new Timer("systemPollTimer", true);
        jiffiesInterface = new GetJiffies() {
            @Override
            public JiffiesAndCpus getTotalSystemJiffies() {
                return SystemPoller.getTotalSystemJiffies();
            }

            @Override
            public long getJiffies(VespaService service) {
                return SystemPoller.getPidJiffies(service);
            }
        };
        lastTotalCpuJiffies = jiffiesInterface.getTotalSystemJiffies();
        for (VespaService s : services) {
            lastCpuJiffiesMetrics.put(s, jiffiesInterface.getJiffies(s));
        }
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
    static long[] getMemoryUsage(VespaService service) {
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

        /* Don't do any work if there are no known services */
        if (services.isEmpty()) {
            schedule();
            return;
        }

        log.log(Level.FINE, () -> "Monitoring system metrics for " + services.size() + " services");

        boolean someAlive = services.stream().anyMatch(VespaService::isAlive);
        lastTotalCpuJiffies = updateMetrics(lastTotalCpuJiffies, startTime/1000, jiffiesInterface, services, lastCpuJiffiesMetrics);

        // If none of the services were alive, reschedule in a short time
        if (!someAlive) {
            reschedule(System.currentTimeMillis() - startTime);
        } else {
            schedule();
        }
    }

    static JiffiesAndCpus updateMetrics(JiffiesAndCpus prevTotalJiffies, long timeStamp, GetJiffies getJiffies,
                                        List<VespaService> services, Map<VespaService, Long> lastCpuJiffiesMetrics) {
        JiffiesAndCpus sysJiffies = getJiffies.getTotalSystemJiffies();
        JiffiesAndCpus sysJiffiesDiff = sysJiffies.diff(prevTotalJiffies);
        for (VespaService s : services) {
            Metrics metrics = new Metrics();
            log.log(Level.FINE, () -> "Current size of system metrics for service  " + s + " is " + metrics.size());

            long[] size = getMemoryUsage(s);
            log.log(Level.FINE, () -> "Updating memory metric for service " + s);

            metrics.add(new Metric(MEMORY_VIRT, size[memoryTypeVirtual], timeStamp));
            metrics.add(new Metric(MEMORY_RSS, size[memoryTypeResident], timeStamp));

            long procJiffies = getJiffies.getJiffies(s);
            long last = lastCpuJiffiesMetrics.get(s);
            long diff = procJiffies - last;

            if (diff >= 0) {
                metrics.add(new Metric(CPU, 100 * sysJiffiesDiff.ratioSingleCoreJiffies(diff), timeStamp));
                metrics.add(new Metric(CPU_UTIL, 100 * sysJiffiesDiff.ratioJiffies(diff), timeStamp));
            }
            lastCpuJiffiesMetrics.put(s, procJiffies);
            s.setSystemMetrics(metrics);
        }
        return sysJiffies;
    }

    static long getPidJiffies(VespaService service) {
        int pid = service.getPid();
        try {
            BufferedReader in = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
            return getPidJiffies(in);
        } catch (FileNotFoundException ex) {
            log.log(Level.FINE, () -> "Unable to find pid " + pid + " in proc directory, for service " + service.getInstanceName());
            service.setAlive(false);
            return 0;
        }
    }
    static long getPidJiffies(BufferedReader in) {
        String line;
        String[] elems;

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

    private static JiffiesAndCpus getTotalSystemJiffies() {
        try {
            BufferedReader in = new BufferedReader(new FileReader("/proc/stat"));
            return getTotalSystemJiffies(in);
        } catch (FileNotFoundException ex) {
            log.log(Level.SEVERE, "Unable to open stat file", ex);
            return new JiffiesAndCpus();
        }
    }
    static JiffiesAndCpus getTotalSystemJiffies(BufferedReader in) {
        ArrayList<CpuJiffies> jiffies = new ArrayList<>();
        CpuJiffies total = null;

        try {
            String line;
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
            return new JiffiesAndCpus();
        }

        /* Normalize so that a process that uses an entire CPU core will get 100% util */
        return (total != null)
                ? new JiffiesAndCpus(total.getTotalJiffies(), jiffies.size())
                : new JiffiesAndCpus();
    }

    private void schedule(long time) {
        try {
            systemPollTimer.schedule(new PollTask(this), time);
        } catch(IllegalStateException e){
            log.info("Tried to schedule task, but timer was already shut down.");
        }
    }

    public void schedule() {
        schedule(pollingIntervalSecs * 1000L);
    }

    private void reschedule(long skew) {
        long sleep = (pollingIntervalSecs * 1000L) - skew;

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

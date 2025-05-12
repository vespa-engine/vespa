// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.model.MetricId;
import com.yahoo.system.ProcessExecuter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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

import static java.util.logging.Level.WARNING;

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

    private final Duration interval;
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

    public SystemPoller(List<VespaService> services, Duration interval) {
        this.services = services;
        this.interval = interval;
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
     * Return memory usage in bytes for a given process, both resident and virtual is
     * returned.
     *
     * @param service The instance to get memory usage for
     * @return array[0] = memoryResident, array[1] = memoryVirtual (both in bytes)
     */
    static long[] getMemoryUsage(VespaService service) {
        return getMemoryUsage(service, getPageSize());
    }

    static long[] getMemoryUsage(VespaService service, int pageSize) {
        String s;
        int pid = service.getPid();

        try {
            s = Files.readString(Path.of("/proc/" + pid + "/statm"));
        } catch (IOException ex) {
            service.setAlive(false);
            return new long[2];
        }
        try {
            return getMemoryUsage(s, pageSize);
        } catch (IOException ex) {
            log.log(Level.FINE, "Unable to read line from statm file", ex);
            return new long[2];
        }
    }

    static long[] getMemoryUsage(String s, int pageSize) throws IOException{
        long[] size = new long[2];
        // statm line: "size rss shared text lib data dt"
        // all values are number of pages, return values from this method are values in bytes
        var statmOutputs = s.split(" ");
        size[memoryTypeVirtual] = Long.parseLong(statmOutputs[0]) * pageSize;
        // Note 1: From man proc_pid_statm man page: rss is the same as VmRSS in /proc/pid/status
        // Note 2: From man proc_pid_status:  VmRSS  Resident set size.  Note that the value here is the sum of RssAnon, RssFile, and  RssShmem.
        // Note 3: 'shared' is RssFile+RssShmem, so subtraction below gives us the same value as RssAnon
        size[memoryTypeResident] = Long.parseLong(statmOutputs[1]) - Long.parseLong(statmOutputs[2]) * pageSize;

        return size;
    }

    /**
     * Poll services for system metrics
     */
    void poll() {
        Instant startTime = Instant.now();

        /* Don't do any work if there are no known services */
        if (services.isEmpty()) {
            schedule();
            return;
        }

        log.log(Level.FINE, () -> "Monitoring system metrics for " + services.size() + " services");

        boolean someAlive = services.stream().anyMatch(VespaService::isAlive);
        lastTotalCpuJiffies = updateMetrics(lastTotalCpuJiffies, startTime, jiffiesInterface, services, lastCpuJiffiesMetrics);

        // If none of the services were alive, reschedule in a short time
        if (!someAlive) {
            reschedule(Duration.between(startTime, Instant.now()));
        } else {
            schedule();
        }
    }

    static JiffiesAndCpus updateMetrics(JiffiesAndCpus prevTotalJiffies, Instant timeStamp, GetJiffies getJiffies,
                                        List<VespaService> services, Map<VespaService, Long> lastCpuJiffiesMetrics) {
        Map<VespaService, Long> currentServiceJiffies = new HashMap<>();
        for (VespaService s : services) {
            currentServiceJiffies.put(s, getJiffies.getJiffies(s));
        }
        JiffiesAndCpus sysJiffies = getJiffies.getTotalSystemJiffies();
        JiffiesAndCpus sysJiffiesDiff = sysJiffies.diff(prevTotalJiffies);
        log.log(Level.FINE, () -> "Total jiffies: " + sysJiffies.jiffies + " - " + prevTotalJiffies.jiffies + " = " + sysJiffiesDiff.jiffies);
        for (VespaService s : services) {
            Metrics metrics = new Metrics();

            long[] size = getMemoryUsage(s);
            log.log(Level.FINE, () -> "Updating memory metric for service " + s);

            metrics.add(new Metric(MEMORY_VIRT, size[memoryTypeVirtual], timeStamp));
            metrics.add(new Metric(MEMORY_RSS, size[memoryTypeResident], timeStamp));

            long procJiffies = currentServiceJiffies.get(s);
            long last = lastCpuJiffiesMetrics.get(s);
            long diff = procJiffies - last;

            log.log(Level.FINE, () -> "Service " + s + " jiffies: " + procJiffies + " - " + last + " = " + diff);
            if (diff >= 0) {
                metrics.add(new Metric(CPU, 100 * sysJiffiesDiff.ratioSingleCoreJiffies(diff), timeStamp));
                metrics.add(new Metric(CPU_UTIL, 100 * sysJiffiesDiff.ratioJiffies(diff), timeStamp));
            }
            lastCpuJiffiesMetrics.put(s, procJiffies);
            s.setSystemMetrics(metrics);
            log.log(Level.FINE, () -> "Current size of system metrics for service  " + s + " is " + metrics.size());
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

    void schedule(Duration time) {
        try {
            systemPollTimer.schedule(new PollTask(this), time.toMillis());
        } catch(IllegalStateException e){
            log.info("Tried to schedule task, but timer was already shut down.");
        }
    }

    void schedule() {
        schedule(interval);
    }

    private void reschedule(Duration skew) {
        Duration sleep = interval.minus(skew);

        // Don't sleep less than 1 min
        if ( sleep.compareTo(Duration.ofMinutes(1)) < 0) {
            schedule(Duration.ofMinutes(1));
        } else {
            schedule(sleep);
        }
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

    private static int getPageSize() {
        try {
            return Integer.parseInt(new ProcessExecuter().exec("getconf PAGESIZE").getSecond().trim());
        } catch (IOException e) {
            log.log(WARNING, "Getting page size failed, using fallback value 4096");
            return 4096;
        }
    }


}

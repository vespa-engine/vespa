// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.statistics.ContainerWatchdogMetrics;
import com.yahoo.metrics.ContainerMetrics;
import com.yahoo.nativec.NativeHeap;
import com.yahoo.security.tls.TlsMetrics;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Uses a timer to emit metrics
 *
 * @author bjorncs
 * @author vegardh
 */
public class MetricUpdater extends AbstractComponent {

    private static final String NATIVE_FREE_MEMORY_BYTES = ContainerMetrics.MEM_NATIVE_FREE.baseName();
    private static final String NATIVE_USED_MEMORY_BYTES = ContainerMetrics.MEM_NATIVE_USED.baseName();
    private static final String NATIVE_TOTAL_MEMORY_BYTES = ContainerMetrics.MEM_NATIVE_TOTAL.baseName();
    private static final String HEAP_FREE_MEMORY_BYTES = ContainerMetrics.MEM_HEAP_FREE.baseName();
    private static final String HEAP_USED_MEMORY_BYTES = ContainerMetrics.MEM_HEAP_USED.baseName();
    private static final String HEAP_TOTAL_MEMORY_BYTES = ContainerMetrics.MEM_HEAP_TOTAL.baseName();
    private static final String DIRECT_FREE_MEMORY_BYTES = ContainerMetrics.MEM_DIRECT_FREE.baseName();
    private static final String DIRECT_USED_MEMORY_BYTES = ContainerMetrics.MEM_DIRECT_USED.baseName();
    private static final String DIRECT_TOTAL_MEMORY_BYTES = ContainerMetrics.MEM_DIRECT_TOTAL.baseName();
    private static final String DIRECT_COUNT = ContainerMetrics.MEM_DIRECT_COUNT.baseName();
    private static final String MEMORY_MAPPINGS_COUNT = ContainerMetrics.JDISC_MEMORY_MAPPINGS.baseName();
    private static final String OPEN_FILE_DESCRIPTORS = ContainerMetrics.JDISC_OPEN_FILE_DESCRIPTORS.baseName();
    private static final String TOTAL_THREADS = "jdisc.threads.total";

    private final Scheduler scheduler;

    @Inject
    public MetricUpdater(Metric metric, ContainerWatchdogMetrics containerWatchdogMetrics) {
        this(new TimerScheduler(), metric, containerWatchdogMetrics);
    }

    MetricUpdater(Scheduler scheduler, Metric metric, ContainerWatchdogMetrics containerWatchdogMetrics) {
        this.scheduler = scheduler;
        scheduler.schedule(new UpdaterTask(metric, containerWatchdogMetrics), Duration.ofSeconds(10));
    }

    @Override
    public void deconstruct() {
        scheduler.cancel();
    }

    // Note: Linux-specific
    private static long count_mappings() {
        long count = 0;
        try {
            Path p = Paths.get("/proc/self/maps");
            if (!p.toFile().exists()) return 0; // E.g. MacOS
            byte[] data = Files.readAllBytes(p);
            for (byte b : data) {
                if (b == '\n') {
                    ++count;
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read /proc/self/maps: " + e);
        }
        return count;
    }
    // Note: Linux-specific

    private static long count_open_files() {
        long count = 0;
        try {
            Path p = Paths.get("/proc/self/fd");
            if (!p.toFile().exists()) return 0; // E.g. MacOS
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {
                for (Path entry : stream) {
                    ++count;
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read /proc/self/fd: " + e);
        }
        return count;
    }

    private static class UpdaterTask implements Runnable {

        private final Runtime runtime = Runtime.getRuntime();
        private final Metric metric;
        private final ContainerWatchdogMetrics containerWatchdogMetrics;
        private final GarbageCollectionMetrics garbageCollectionMetrics;
        private final JrtMetrics jrtMetrics;
        private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        private TlsMetrics.Snapshot tlsMetricsSnapshot = TlsMetrics.Snapshot.EMPTY;

        public UpdaterTask(Metric metric, ContainerWatchdogMetrics containerWatchdogMetrics) {
            this.metric = metric;
            this.containerWatchdogMetrics = containerWatchdogMetrics;
            this.garbageCollectionMetrics = new GarbageCollectionMetrics(Clock.systemUTC());
            this.jrtMetrics = new JrtMetrics(metric);
        }

        private void directMemoryUsed() {
            long count = 0;
            long used = 0;
            long total = 0;
            for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                count += pool.getCount();
                used += pool.getMemoryUsed();
                total += pool.getTotalCapacity();
            }
            metric.set(DIRECT_FREE_MEMORY_BYTES, total - used, null);
            metric.set(DIRECT_USED_MEMORY_BYTES, used, null);
            metric.set(DIRECT_TOTAL_MEMORY_BYTES, total, null);
            metric.set(DIRECT_COUNT, count, null);
        }

        private void nativeHeapUsed() {
            NativeHeap nativeHeap = NativeHeap.sample();
            metric.set(NATIVE_FREE_MEMORY_BYTES, nativeHeap.availableSize(), null);
            metric.set(NATIVE_USED_MEMORY_BYTES, nativeHeap.usedSize(), null);
            metric.set(NATIVE_TOTAL_MEMORY_BYTES, nativeHeap.totalSize(), null);
        }

        private void jvmDetails() {
            Metric.Context ctx = metric.createContext(Map.of(
                    "version", System.getProperty("java.runtime.version"),
                    "home", System.getProperty("java.home"),
                    "vendor", System.getProperty("java.vm.vendor"),
                    "arch", System.getProperty("os.arch")));
            metric.set("jdisc.jvm", Runtime.version().feature(), ctx);
        }

        private void tlsMetrics() {
            var newSnapshot = TlsMetrics.instance().snapshot();
            var diff = newSnapshot.changesSince(tlsMetricsSnapshot);
            metric.add(ContainerMetrics.JDISC_TLS_CAPABILITY_CHECKS_SUCCEEDED.baseName(), diff.capabilityChecksSucceeded(), null);
            metric.add(ContainerMetrics.JDISC_TLS_CAPABILITY_CHECKS_FAILED.baseName(), diff.capabilityChecksFailed(), null);
            tlsMetricsSnapshot = newSnapshot;
        }

        @Override
        public void run() {
            long freeMemory = runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long usedMemory = totalMemory - freeMemory;
            metric.set(HEAP_FREE_MEMORY_BYTES, freeMemory, null);
            metric.set(HEAP_USED_MEMORY_BYTES, usedMemory, null);
            metric.set(HEAP_TOTAL_MEMORY_BYTES, totalMemory, null);
            metric.set(MEMORY_MAPPINGS_COUNT, count_mappings(), null);
            metric.set(OPEN_FILE_DESCRIPTORS, count_open_files(), null);
            metric.set(TOTAL_THREADS, threadMXBean.getThreadCount(), null);
            directMemoryUsed();
            nativeHeapUsed();

            containerWatchdogMetrics.emitMetrics(metric);
            garbageCollectionMetrics.emitMetrics(metric);
            jrtMetrics.emitMetrics();
            jvmDetails();
            tlsMetrics();
        }
    }

    private static class TimerScheduler implements Scheduler {

        private final Timer timer = new Timer();

        @Override
        public void schedule(Runnable runnable, Duration frequency) {
            long frequencyMillis = frequency.toMillis();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    runnable.run();
                }
            }, frequencyMillis, frequencyMillis) ;
        }

        @Override
        public void cancel() {
            timer.cancel();
        }
    }

    interface Scheduler {
        void schedule(Runnable runnable, Duration frequency);
        void cancel();
    }

}


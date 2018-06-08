// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.statistics.ContainerWatchdogMetrics;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Uses a timer to emit metrics
 *
 * @author bjorncs
 * @author vegardh
 *
 */
public class MetricUpdater extends AbstractComponent {

    @Deprecated private static final String DEPRECATED_FREE_MEMORY_BYTES = "freeMemoryBytes";
    @Deprecated private static final String DEPRECATED_USED_MEMORY_BYTES = "usedMemoryBytes";
    @Deprecated private static final String DEPRECATED_TOTAL_MEMORY_BYTES = "totalMemoryBytes";
    private static final String FREE_MEMORY_BYTES = "mem.heap.free";
    private static final String USED_MEMORY_BYTES = "mem.heap.used";
    private static final String TOTAL_MEMORY_BYTES = "mem.heap.total";
    private static final String MEMORY_MAPPINGS_COUNT = "jdisc.memory_mappings";
    private static final String OPEN_FILE_DESCRIPTORS = "jdisc.open_file_descriptors";

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

        public UpdaterTask(Metric metric, ContainerWatchdogMetrics containerWatchdogMetrics) {
            this.metric = metric;
            this.containerWatchdogMetrics = containerWatchdogMetrics;
            this.garbageCollectionMetrics = new GarbageCollectionMetrics(Clock.systemUTC());
        }

        @SuppressWarnings("deprecation")
        @Override
        public void run() {
            long freeMemory = runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long usedMemory = totalMemory - freeMemory;
            metric.set(DEPRECATED_FREE_MEMORY_BYTES, freeMemory, null);
            metric.set(DEPRECATED_USED_MEMORY_BYTES, usedMemory, null);
            metric.set(DEPRECATED_TOTAL_MEMORY_BYTES, totalMemory, null);
            metric.set(FREE_MEMORY_BYTES, freeMemory, null);
            metric.set(USED_MEMORY_BYTES, usedMemory, null);
            metric.set(TOTAL_MEMORY_BYTES, totalMemory, null);
            metric.set(MEMORY_MAPPINGS_COUNT, count_mappings(), null);
            metric.set(OPEN_FILE_DESCRIPTORS, count_open_files(), null);

            containerWatchdogMetrics.emitMetrics(metric);
            garbageCollectionMetrics.emitMetrics(metric);
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


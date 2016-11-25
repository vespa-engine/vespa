// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.metric;

import java.util.Timer;
import java.util.TimerTask;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;

/**
 * Uses a timer to emit metrics
 * 
 * @author vegardh
 * @since 5.17
 *
 */
public class MetricUpdater extends AbstractComponent {

    private static final String FREE_MEMORY_BYTES = "freeMemoryBytes";
    private static final String USED_MEMORY_BYTES = "usedMemoryBytes";
    private static final String TOTAL_MEMORY_BYTES = "totalMemoryBytes";
    private static final String MANHATTAN_FREE_MEMORY_BYTES = "mem.heap.free";
    private static final String MANHATTAN_USED_MEMORY_BYTES = "mem.heap.used";
    private static final String MANHATTAN_TOTAL_MEMORY_BYTES = "mem.heap.total";
    private static final String MEMORY_MAPPINGS_COUNT = "memory_mappings_count";

    private final Metric metric;
    private final Timer timer = new Timer();
    long freeMemory = -1;
    long totalMemory = -1;

    @Inject
    public MetricUpdater(Metric metric) {
        this(metric, 10*1000);
    }
    
    public MetricUpdater(Metric metric, long delayMillis) {
        this.metric = metric;
        timer.schedule(new UpdaterTask(), delayMillis, delayMillis);
    }
    
    @Override
    public void deconstruct() {
        if (timer!=null) timer.cancel();
    }

    // For testing
    long getFreeMemory() { return freeMemory; }
    long getTotalMemory() { return totalMemory; }

    private class UpdaterTask extends TimerTask {
        @Override
        public void run() {
            freeMemory = Runtime.getRuntime().freeMemory();
            totalMemory = Runtime.getRuntime().totalMemory();
            long usedMemory = totalMemory - freeMemory;
            metric.set(FREE_MEMORY_BYTES, freeMemory, null);
            metric.set(USED_MEMORY_BYTES, usedMemory, null);
            metric.set(TOTAL_MEMORY_BYTES, totalMemory, null);
            metric.set(MANHATTAN_FREE_MEMORY_BYTES, freeMemory, null);
            metric.set(MANHATTAN_USED_MEMORY_BYTES, usedMemory, null);
            metric.set(MANHATTAN_TOTAL_MEMORY_BYTES, totalMemory, null);
            metric.set(MEMORY_MAPPINGS_COUNT, count_mappings(), null);
        }

        // Note: Linux-specific
        private long count_mappings() {
            long count = 0;
            try {
                Path p = Paths.get("/proc/self/maps");
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
    }
}


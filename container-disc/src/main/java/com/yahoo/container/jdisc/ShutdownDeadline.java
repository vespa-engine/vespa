// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.vespa.defaults.Defaults;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.yahoo.protect.Process.dumpHeap;
import static com.yahoo.protect.Process.logAndDie;

/**
 * Kills the JVM if the application is unable to shutdown before deadline.
 *
 * @author bjorncs
 */
class ShutdownDeadline implements AutoCloseable {

    private final String configId;
    private final ScheduledThreadPoolExecutor executor;

    ShutdownDeadline(String configId) {
        this.configId = configId;
        executor = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("Shutdown deadline timer"));
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    ShutdownDeadline schedule(long millis, boolean heapdumpOnShutdown) {
        executor.schedule(() -> onDeadline(heapdumpOnShutdown), millis, TimeUnit.MILLISECONDS);
        return this;
    }

    void cancel() { executor.shutdownNow(); }
    @Override public void close() { cancel();  }

    private void onDeadline(boolean heapdumpOnShutdown) {
        if (heapdumpOnShutdown) dumpHeap(heapdumpFilename(), true);
        logAndDie("Timed out waiting for application shutdown. Please check that all your request handlers " +
                "drain their request content channels.", true);
    }

    private String heapdumpFilename() {
        return Defaults.getDefaults().underVespaHome("var/crash/java_pid.") + sanitizeFileName(configId) + "."
                + ProcessHandle.current().pid() + ".hprof";
    }

    static String sanitizeFileName(String s) {
        return s.trim().replace('\\', '.').replaceAll("[/,;]", ".");
    }

}

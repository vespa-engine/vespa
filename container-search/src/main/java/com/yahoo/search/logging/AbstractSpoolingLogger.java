// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.logging;

import com.yahoo.concurrent.DaemonThreadFactory;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Abstract class that deals with storing event entries on disk and making sure all stored
 * entries are eventually sent
 *
 * @author hmusum
 */
abstract class AbstractSpoolingLogger extends AbstractThreadedLogger implements Runnable {

    protected static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Spooler.class.getName());

    private static final ScheduledExecutorService executorService =
            new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("AbstractSpoolingLogger-send-"));

    protected final Spooler spooler;

    public AbstractSpoolingLogger() {
        this(new Spooler());
    }

    public AbstractSpoolingLogger(Spooler spooler) {
        this.spooler = spooler;
        executorService.scheduleWithFixedDelay(this, 0, 10L, TimeUnit.MILLISECONDS);
    }

    public void run() {
        try {
            var entries = spooler.processFiles();
            log.log(Level.INFO, "Entries: " + entries.size());
            entries.forEach(this::transport);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean send(LoggerEntry entry) {
        log.log(Level.INFO, "Sending");
        try {
            executor.execute(() -> spooler.write(entry));
        } catch (RejectedExecutionException e) {
            return false;
        }
        return true;
    }

    // TODO Call from a component or make this class a component
    public void shutdown() {
        executorService.shutdown();
        try {
            if ( ! executorService.awaitTermination(10, TimeUnit.SECONDS))
                log.log(Level.WARNING, "Timeout elapsed waiting for termination");
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Failure when waiting for termination: " + e.getMessage());
        }
    }

}

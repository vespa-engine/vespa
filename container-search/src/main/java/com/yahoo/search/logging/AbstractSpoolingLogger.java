// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.logging;

import com.yahoo.concurrent.DaemonThreadFactory;

import java.time.Clock;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Abstract class that deals with storing event entries on disk and making sure all stored
 * entries are eventually sent. Note that the {@link #start()} method needs to be called by subclasses as
 * the last statement in their constructor.
 *
 * @author hmusum
 */
public abstract class AbstractSpoolingLogger extends AbstractThreadedLogger implements Runnable {

    protected static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Spooler.class.getName());

    private final ScheduledExecutorService executorService;
    protected final Spooler spooler;

    @SuppressWarnings("unused") // Used by subclasses
    public AbstractSpoolingLogger() {
        this(new Spooler(Clock.systemUTC()));
    }

    public AbstractSpoolingLogger(Spooler spooler) {
        this.spooler = spooler;
        this.executorService = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("AbstractSpoolingLogger-send-"));
    }

    /** Start processing files, must be called by subclasses */
    public void start() {
        this.executorService.scheduleWithFixedDelay(this, 0, 1L, TimeUnit.SECONDS);
    }

    public void run() {
        try {
            spooler.switchFileIfNeeded();
            spooler.processFiles(this::transport);
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception when processing files: " + e.getMessage());
        }
    }

    @Override
    public boolean send(LoggerEntry entry) {
        log.log(Level.FINE, "Sending entry " + entry + " to spooler");
        try {
            executor.execute(() -> spooler.write(entry));
        } catch (RejectedExecutionException e) {
            return false;
        }
        return true;
    }

    @Deprecated
    /*
      @deprecated use {@link #deconstruct()} instead
     */
    public void shutdown() { deconstruct(); }

    @Override
    public void deconstruct() {
        super.deconstruct();
        executorService.shutdown();
        try {
            if ( ! executorService.awaitTermination(10, TimeUnit.SECONDS))
                log.log(Level.WARNING, "Timeout elapsed waiting for termination");
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Failure when waiting for termination: " + e.getMessage());
        }
        run();  // Run a last time to make sure all data is written to file
    }

}

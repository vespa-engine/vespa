// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.StatisticsConfig;

/**
 * Centralized book keeping for statistics module. Times logging and reads
 * config. It is normally obtained through injection, but may be initialized
 * manually for testing or when an injection mechanism is unavailable. Logging
 * will be disabled by initializing the Statistics class with a null config
 * object.
 *
 * @author Steinar Knutsen
 */
public final class StatisticsImpl extends AbstractComponent implements Statistics {

    private final Timer worker;
    private final StatisticsConfig config;
    private static final Logger log = Logger.getLogger(StatisticsImpl.class.getName());
    private final int collectioninterval;
    private final int logginginterval;
    // default access for testing only
    final Map<String, Handle> handles = new HashMap<>();

    /**
     * Build a statistics manager based on the given config values. Use a config
     * builder for testing if logging is necessary, set the config argument to
     * the constructor to null is logging is not necessary.
     *
     * @since 5.1.4
     * @param config
     *            settings for logging interval and configured events. Setting
     *            it to null disables logging.
     * @throws IllegalArgumentException
     *             if logging interval is smaller than collection interval, or
     *             collection interval is not a multiplum of logging interval
     */
    public StatisticsImpl(StatisticsConfig config) {
        int l = (int) config.loggingintervalsec();
        int c = (int) config.collectionintervalsec();

        if (l != 0 && l < c) {
            throw new IllegalArgumentException(
                    "Logging interval (" + l + ") smaller than collection interval (" + c + ")."
                            + " New config ignored.");
        }
        if ((l % c) != 0) {
            throw new IllegalArgumentException(
                    "Collection interval (" + c + ") not multiplum of logging interval (" + l + ")."
                            + " New config ignored.");
        }
        this.logginginterval = l;
        this.collectioninterval = c;
        this.config = config;
        this.worker = new Timer(true);
    }

    /**
     * Cancel internal worker thread and do any other necessary cleanup. The
     * internal worker thread is a daemon thread, so not calling this will not
     * hamper a clean exit from the VM.
     */
    @Override
    public void deconstruct() {
        worker.cancel();
    }

    private void schedule(Handle h) {
        if (logginginterval != 0) {
            h.run();
            // We use the rather creative assumption that there is
            // exactly 24h pr day+night.
            final Date d = new Date();
            final long ms = collectioninterval * 1000L;
            final long delay = ms - (d.getTime() % (ms));
            worker.scheduleAtFixedRate(h.makeTask(), delay, ms);
        }
    }

    /**
     * Add a new handle to be scheduled for periodic logging. If a handle
     * already exists with the same name, it will be cancelled and removed from
     * the internal state of this object.
     */
    @Override
    public void register(Handle h) {
        synchronized (handles) {
            Handle oldHandle = handles.get(h.getName());
            if (oldHandle == h) {
                log.log(Level.WARNING, "Handle [" + h + "] already registered");
                return;
            }
            if (oldHandle != null) {
                oldHandle.cancel();
            }
            handles.put(h.getName(), h);
            if (worker != null) {
                schedule(h);
            }
        }
    }

    /**
     * Remove a named handler from the set of working handlers.
     */
    @Override
    public void remove(String name) {
        synchronized (handles) {
            Handle oldHandle = handles.remove(name);
            if (oldHandle != null) {
                oldHandle.cancel();
            }
        }
    }

    /**
     * Get current config used. This may be a null reference, depending on how
     * the instance was constructed.
     */
    @Override
    public StatisticsConfig getConfig() {
        return config;
    }

    /**
     * Purges all cancelled Handles from internal Map and Timer.
     *
     * @return return value from java.util.Timer.purge()
     */
    @Override
    public int purge() {
        synchronized (handles) {
            Iterator<Handle> it = handles.values().iterator();
            while (it.hasNext()) {
                final Handle h = it.next();
                if (h.isCancelled()) {
                    it.remove();
                }
            }
            return worker == null ? 0 : worker.purge();
        }
    }

}

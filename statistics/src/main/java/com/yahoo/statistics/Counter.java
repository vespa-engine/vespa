// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


import java.util.concurrent.atomic.AtomicLong;

import com.yahoo.log.event.Event;
import com.yahoo.container.StatisticsConfig;


/**
 * A single integer value which can be incremented.
 *
 * @author  Steinar Knutsen
 */
public class Counter extends Handle {
    // The current value of this counter
    private AtomicLong current = new AtomicLong(0L);

    // Whether or not this counter shall be reset between each logging
    // interval
    private final boolean resetCounter;

    /**
     * A monotonically increasing 64 bit integer value.
     *
     * @param name
     *            The name of this counter, for use in logging.
     * @param manager
     *            the statistics manager acquired by injection
     * @param fetchParametersFromConfig
     *            Whether or not this counter should be initialized from config.
     */
    public Counter(String name, Statistics manager, boolean fetchParametersFromConfig) {
        this(name, manager, fetchParametersFromConfig, null, false, true);
    }

    /**
     * A monotonically increasing 64 bit integer value.
     *
     * @param name
     *            The name of this counter, for use in logging.
     * @param manager
     *            the statistics manager acquired by injection
     * @param fetchParametersFromConfig
     *            Whether or not this counter should be initialized from config.
     * @param callback
     *            will be invoked each time this counter is written to the log
     * @param resetCounter
     *            Control for if this Counter should be reset between each
     *            logging interval.
     */
    public Counter(String name, Statistics manager,
                   boolean fetchParametersFromConfig, Callback callback, boolean resetCounter) {
                this(name, manager, fetchParametersFromConfig, callback,
                        resetCounter, true);
            }

    /**
     * A monotonically increasing 64 bit integer value. Do not make this
     * constructor public, it is used for creating unregistered counters.
     *
     * @param name
     *            The name of this counter, for use in logging.
     * @param manager
     *            the statistics manager acquired by injection
     * @param fetchParametersFromConfig
     *            Whether or not this counter should be initialized from config.
     * @param callback
     *            will be invoked each time this counter is written to the log
     * @param resetCounter
     *            Control for if this Counter should be reset between each
     *            logging interval.
     * @param register
     *            Whether to register the counter in the statistics manager
     */
    private Counter(String name, Statistics manager,
            boolean fetchParametersFromConfig, Callback callback,
            boolean resetCounter, boolean register) {
        super(name, manager, callback);
        if (fetchParametersFromConfig) {
            StatisticsConfig config = manager.getConfig();
            this.resetCounter = getResetCounter(name, config);
        } else {
            this.resetCounter = resetCounter;
        }
        if (register) {
            manager.register(this);
        }
    }


    /**
     * Get a Counter instance not registered in the statistics manager. This is
     * used by CounterGroup and should not be made public.
     *
     * @param name
     *            The name of this counter, for use in logging.
     * @param resetCounter
     *            Control for if this Counter should be reset between each
     *            logging interval.
     */
    static Counter intializeUnregisteredCounter(String name,
            boolean resetCounter) {
        return new Counter(name, null, false, null, resetCounter, false);
    }

    /**
     * If this Counter is set up to read config, configure it
     * according to the config given.
     */
    private static boolean getResetCounter(String name, StatisticsConfig config) {
        for (int i = 0; i < config.counterresets().size(); i++) {
            String configName = config.counterresets(i).name();
            if (configName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Increment by 1.
     */
    public void increment() {
        current.incrementAndGet();
    }

    /**
     * Increment by n.
     */
    public void increment(long n) {
        current.addAndGet(n);
    }

    /**
     * @return current value of this counter
     */
    public long get() {
        return current.get();
    }

    /**
     * The reset counter is true if this is counter is reset to 0 between each
     * logging interval.
     *
     * @return whether this counter is reset between each logging interval.
     */
    public boolean getResetCounter() {
        return resetCounter;
    }

    /**
     * If this counter should be set to 0 between each logging interval,
     * do that.
     */
    public void reset() {
        if (resetCounter) {
            current.set(0L);
        }
    }

    /**
     * Log current state and reset.
     */
    @Override
    public void runHandle() {
        String name = getName();
        long lastCurrent;
        boolean resetState = getResetCounter();

        if (resetState) {
            lastCurrent = current.getAndSet(0L);
            Event.value(name, lastCurrent);
        } else {
            lastCurrent = current.get();
            Event.count(name, lastCurrent);
        }
    }

    @Override
    public String toString() {
        return super.toString() + " " + getName() + " " + current;
    }

    CounterProxy getProxyAndReset() {
        CounterProxy c = new CounterProxy(getName());
        if (getResetCounter()) {
            c.setRaw(current.getAndSet(0L));
        } else {
            c.setRaw(current.get());
        }
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() != this.getClass()) {
            return false;
        }
        Counter other = (Counter) o;
        return getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode() + 31 * "Counter".hashCode();
    }

}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


import com.yahoo.container.StatisticsConfig;
import com.yahoo.log.event.Event;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;


/**
 * A set of associated counters.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class CounterGroup extends Group {
    private final boolean resetCounter;

    // A map for names of subevents and Value instances
    private Map<String, Counter> subEvents = new HashMap<>();

    /**
     * @param name The symbolic name of this group of counters.
     */
    public CounterGroup(String name, Statistics manager) {
        this(name, manager, true);
    }

    /**
     * Create a basic group of counter which may or may not depend on config.
     *
     * @param name
     *            The symbolic name of this group of counters.
     * @param manager
     *            the statistics manager acquired by injection
     * @param fetchParametersFromConfig
     *            Whether this Group should be configured from config.
     */
    public CounterGroup(String name, Statistics manager,
            boolean fetchParametersFromConfig) {
        this(name, manager, fetchParametersFromConfig, null, false);
    }

    /**
     * Create a group of counters with a callback included.
     *
     * @param name
     *            The symbolic name of this group of counters.
     * @param manager
     *            the statistics manager acquired by injection
     * @param fetchParametersFromConfig
     *            Whether this Group should be configured from config.
     * @param callback
     *            will be invoked each time data is written to the log
     * @param resetCounter
     *            Control for if this group should be reset between each
     *            logging interval.
     */
    public CounterGroup(String name, Statistics manager,
            boolean fetchParametersFromConfig, Callback callback, boolean resetCounter) {

        super(name, manager, callback);
        if (fetchParametersFromConfig) {
            StatisticsConfig config = manager.getConfig();
            this.resetCounter = getResetCounter(name, config);
        } else {
            this.resetCounter = resetCounter;
        }
        manager.register(this);
    }

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
     * Increment named contained counter by 1.
     */
    public void increment(String name) {
        Counter c = getCounter(name);
        c.increment();
    }

    /**
     * Increment named contained counter by n.
     */
    public void increment(String name, long n) {
        Counter c = getCounter(name);
        c.increment(n);
    }

    /**
     * Get a counter with a given name, creates a new counter if no
     * counter with the name given exists.
     */
    synchronized Counter getCounter(String name) {
        Counter c = subEvents.get(name);
        if (c == null) {
            c = getNewCounter(name);
        }
        return c;
    }

    private Counter getNewCounter(String subName) {
        Counter c = Counter.intializeUnregisteredCounter(subName, resetCounter);
        subEvents.put(subName, c);
        return c;
    }

    /**
     * Dump contained counters to log and reset.
     */
    @Override
    public void runHandle() {
        StringBuilder multi = new StringBuilder();
        CounterProxy[] proxies;
        int i = 0;

        // this is to make sure the number of events does not change while logging
        synchronized (this) {
            proxies = new CounterProxy[subEvents.size()];
            i = 0;
            for (Iterator<Counter> j = subEvents.values().iterator(); j
                    .hasNext();) {
                Counter c = j.next();
                proxies[i] = c.getProxyAndReset();
                i++;
            }
        }

        while (i > 0) {
            i--;
            if (multi.length() > 0) {
                multi.append(", ");
            }
            multi.append(proxies[i].getName());
            multi.append("=");
            multi.append(proxies[i].getRaw());
        }

        Event.countGroup(getName(), multi.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() != this.getClass()) {
            return false;
        }
        CounterGroup other = (CounterGroup) o;
        return getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode() + 31 * "CounterGroup".hashCode();
    }
}

